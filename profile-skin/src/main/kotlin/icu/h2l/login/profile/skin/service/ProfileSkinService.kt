package icu.h2l.login.profile.skin.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.profile.ProfileSkinApplyEvent
import icu.h2l.api.event.profile.ProfileSkinPreprocessEvent
import icu.h2l.api.log.debug
import icu.h2l.api.log.error
import icu.h2l.api.profile.skin.ProfileSkinModel
import icu.h2l.api.profile.skin.ProfileSkinSource
import icu.h2l.api.profile.skin.ProfileSkinTextures
import icu.h2l.login.profile.skin.config.MineSkinMethod
import icu.h2l.login.profile.skin.config.ProfileSkinConfig
import icu.h2l.login.profile.skin.db.ProfileSkinCacheRepository
import icu.h2l.login.profile.skin.db.ProfileSkinCacheRepository.SaveResult
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

class ProfileSkinService(
    private val config: ProfileSkinConfig,
    private val repository: ProfileSkinCacheRepository
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Subscribe
    fun onPreprocess(event: ProfileSkinPreprocessEvent) {
        if (!config.enabled) return

        val profileId = event.hyperZonePlayer.getDBProfile()?.id ?: run {
            debug {
                "[ProfileSkinFlow] preprocess skip: no DB profile, player=${event.hyperZonePlayer.userName}, entry=${event.entryId}"
            }
            return
        }
        val upstreamTextures = event.textures ?: extractTextures(event.authenticatedProfile)
        val source = (event.source ?: extractSkinSource(upstreamTextures))?.normalized()
        val sourceHash = source?.let(::sourceHash)
        val trustedSignedEntry = isTrustedSignedEntry(event.entryId)
        val shouldTrustSignedTextures = upstreamTextures?.isSigned == true
                && config.preferUpstreamSignedTextures
                && trustedSignedEntry
        val shouldForceRestoreSignedTextures = upstreamTextures?.isSigned == true && !trustedSignedEntry
        val shouldAttemptRestore = source != null && (shouldForceRestoreSignedTextures || config.restoreUnsignedTextures)

        debug {
            "[ProfileSkinFlow] preprocess start: profile=$profileId, player=${event.hyperZonePlayer.userName}, entry=${event.entryId}, server=${event.serverUrl}, upstream=${describeTextures(upstreamTextures)}, source=${describeSource(source)}, sourceHash=${shortHash(sourceHash)}, preferSigned=${config.preferUpstreamSignedTextures}, trustedSignedEntry=$trustedSignedEntry, restoreUnsigned=${config.restoreUnsignedTextures}"
        }

        if (shouldTrustSignedTextures) {
            logSaveResult(
                repository.save(profileId, source, upstreamTextures, sourceHash),
                profileId,
                source,
                sourceHash,
                "trusted signed upstream"
            )
            event.textures = upstreamTextures
            debug {
                "[ProfileSkinFlow] preprocess selected signed upstream textures: profile=$profileId, source=${describeSource(source)}, textures=${describeTextures(upstreamTextures)}"
            }
            return
        }

        if (upstreamTextures?.isSigned == true && !trustedSignedEntry) {
            debug {
                "[ProfileSkinFlow] preprocess signed upstream not trusted: profile=$profileId, entry=${event.entryId}, source=${describeSource(source)}"
            }
        }

        if (shouldAttemptRestore) {
            repository.findBySourceHash(sourceHash!!)?.let { cached ->
                logSaveResult(
                    repository.save(profileId, source, cached.textures, sourceHash),
                    profileId,
                    source,
                    sourceHash,
                    "source cache hit"
                )
                event.textures = cached.textures
                debug {
                    "[ProfileSkinFlow] preprocess source cache hit: profile=$profileId, sourceHash=${shortHash(sourceHash)}, cachedProfile=${cached.profileId}, textures=${describeTextures(cached.textures)}"
                }
                return
            }

            debug {
                "[ProfileSkinFlow] preprocess source cache miss, restoring via MineSkin: profile=$profileId, source=${describeSource(source)}, sourceHash=${shortHash(sourceHash)}"
            }

            runCatching {
                restoreTextures(source)
            }.onSuccess { restored ->
                logSaveResult(
                    repository.save(profileId, source, restored, sourceHash),
                    profileId,
                    source,
                    sourceHash,
                    "restored textures"
                )
                event.textures = restored
                debug {
                    "[ProfileSkinFlow] preprocess MineSkin restore success: profile=$profileId, source=${describeSource(source)}, textures=${describeTextures(restored)}"
                }
                return
            }.onFailure { throwable ->
                error(throwable) { "Profile skin restore failed for profile=$profileId: ${throwable.message}" }
            }
        } else {
            debug {
                "[ProfileSkinFlow] preprocess restore skipped: profile=$profileId, reason=${describeRestoreSkipReason(source, shouldForceRestoreSignedTextures)}, upstream=${describeTextures(upstreamTextures)}"
            }
        }

        if (upstreamTextures != null) {
            logSaveResult(
                repository.save(profileId, source, upstreamTextures, sourceHash),
                profileId,
                source,
                sourceHash,
                "upstream fallback"
            )
            event.textures = upstreamTextures
            debug {
                "[ProfileSkinFlow] preprocess fallback to upstream textures: profile=$profileId, textures=${describeTextures(upstreamTextures)}, source=${describeSource(source)}"
            }
        } else {
            debug {
                "[ProfileSkinFlow] preprocess finished without textures: profile=$profileId, source=${describeSource(source)}"
            }
        }
    }

    @Subscribe
    fun onApply(event: ProfileSkinApplyEvent) {
        if (!config.enabled) return

        val profileId = event.hyperZonePlayer.getDBProfile()?.id ?: run {
            debug {
                "[ProfileSkinFlow] apply listener skip: no DB profile, player=${event.hyperZonePlayer.userName}"
            }
            return
        }
        debug {
            "[ProfileSkinFlow] apply listener start: profile=$profileId, player=${event.hyperZonePlayer.userName}, base=${describeProfile(event.baseProfile)}"
        }
        repository.findByProfileId(profileId)?.let { cached ->
            event.textures = cached.textures
            debug {
                "[ProfileSkinFlow] apply listener cache hit: profile=$profileId, sourceHash=${shortHash(cached.sourceHash)}, textures=${describeTextures(cached.textures)}"
            }
            return
        }

        debug {
            "[ProfileSkinFlow] apply listener cache miss: profile=$profileId, allowInitialFallback=${config.allowInitialProfileFallback}"
        }

        if (!config.allowInitialProfileFallback) {
            debug {
                "[ProfileSkinFlow] apply listener fallback disabled: profile=$profileId"
            }
            return
        }

        val initialProfile = event.hyperZonePlayer.getInitialGameProfile()
        val initialTextures = extractTextures(initialProfile)
        val baseTextures = extractTextures(event.baseProfile)
        val fallbackTextures = initialTextures ?: baseTextures
        if (fallbackTextures != null) {
            event.textures = fallbackTextures
            debug {
                "[ProfileSkinFlow] apply listener fallback selected: profile=$profileId, source=${if (initialTextures != null) "initialGameProfile" else "baseProfile"}, initial=${describeProfile(initialProfile)}, base=${describeProfile(event.baseProfile)}, textures=${describeTextures(fallbackTextures)}"
            }
        } else {
            debug {
                "[ProfileSkinFlow] apply listener no fallback textures: profile=$profileId, initial=${describeProfile(initialProfile)}, base=${describeProfile(event.baseProfile)}"
            }
        }
    }

    private fun restoreTextures(source: ProfileSkinSource): ProfileSkinTextures {
        val body = when (MineSkinMethod.from(config.mineSkin.method)) {
            MineSkinMethod.URL -> restoreByUrl(source)
            MineSkinMethod.UPLOAD -> restoreByUpload(source)
        }
        return parseMineSkinResponse(body)
    }

    private fun restoreByUrl(source: ProfileSkinSource): String {
        val payload = JsonObject().apply {
            addProperty("name", UUID.randomUUID().toString().substring(0, 6))
            addProperty("variant", source.model)
            addProperty("visibility", 0)
            addProperty("url", source.skinUrl)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.mineSkin.urlEndpoint))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("MineSkin URL restore failed: HTTP ${response.statusCode()}, body=${response.body()}")
        }
        return response.body()
    }

    private fun restoreByUpload(source: ProfileSkinSource): String {
        val bytes = requireValidSkin(source.skinUrl)
        val boundary = "----HyperZoneLogin${UUID.randomUUID().toString().replace("-", "")}"
        val separator = "--$boundary\r\n"
        val end = "--$boundary--\r\n"
        val body = ByteArrayOutputStream().use { output ->
            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"name\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(UUID.randomUUID().toString().substring(0, 6).toByteArray(StandardCharsets.UTF_8))
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"variant\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(source.model.toByteArray(StandardCharsets.UTF_8))
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"visibility\"\r\n\r\n0\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"upload.png\"\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Type: image/png\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(bytes)
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(end.toByteArray(StandardCharsets.UTF_8))
            output.toByteArray()
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.mineSkin.uploadEndpoint))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("MineSkin upload restore failed: HTTP ${response.statusCode()}, body=${response.body()}")
        }
        return response.body()
    }

    private fun parseMineSkinResponse(body: String): ProfileSkinTextures {
        val root = JsonParser.parseString(body).asJsonObject
        val texture = root.getAsJsonObject("data")
            ?.getAsJsonObject("texture")
            ?: throw IllegalStateException("MineSkin response missing data.texture: $body")

        val value = texture.getAsJsonPrimitive("value")?.asString
            ?: throw IllegalStateException("MineSkin response missing value: $body")
        val signature = texture.getAsJsonPrimitive("signature")?.asString
        return ProfileSkinTextures(value = value, signature = signature)
    }

    private fun requireValidSkin(skinUrl: String): ByteArray {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(skinUrl))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Skin download failed: HTTP ${response.statusCode()}")
        }

        val bytes = response.body()
        ByteArrayInputStream(bytes).use { input ->
            val image: BufferedImage = ImageIO.read(input)
                ?: throw IllegalStateException("Skin image decode failed")
            if (image.width != 64) {
                throw IllegalStateException("Skin width is not 64")
            }
            if (image.height != 32 && image.height != 64) {
                throw IllegalStateException("Skin height is not 64 or 32")
            }
        }
        return bytes
    }

    private fun extractTextures(profile: GameProfile?): ProfileSkinTextures? {
        val property = profile?.properties?.firstOrNull { it.name.equals("textures", ignoreCase = true) } ?: return null
        return ProfileSkinTextures(property.value, property.signature)
    }

    private fun extractSkinSource(textures: ProfileSkinTextures?): ProfileSkinSource? {
        val value = textures?.value ?: return null
        val decoded = String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        val root = JsonParser.parseString(decoded).asJsonObject
        val skin = root.getAsJsonObject("textures")
            ?.getAsJsonObject("SKIN")
            ?: return null
        val url = skin.getAsJsonPrimitive("url")?.asString ?: return null
        val model = skin.getAsJsonObject("metadata")
            ?.getAsJsonPrimitive("model")
            ?.asString
        return ProfileSkinSource(url, ProfileSkinModel.normalize(model))
    }

    private fun sourceHash(source: ProfileSkinSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val value = "${source.skinUrl}|${source.model}".toByteArray(StandardCharsets.UTF_8)
        return digest.digest(value).joinToString("") { "%02x".format(it) }
    }

    private fun describeProfile(profile: GameProfile?): String {
        if (profile == null) {
            return "null"
        }
        return "id=${profile.id}, name=${profile.name}, properties=${profile.properties.size}, textures=${describeTextures(extractTextures(profile))}"
    }

    private fun describeTextures(textures: ProfileSkinTextures?): String {
        if (textures == null) {
            return "none"
        }
        return "present(valueLength=${textures.value.length}, signed=${textures.isSigned})"
    }

    private fun describeSource(source: ProfileSkinSource?): String {
        if (source == null) {
            return "none"
        }
        return "url=${source.skinUrl}, model=${source.model}"
    }

    private fun shortHash(value: String?): String {
        if (value.isNullOrBlank()) {
            return "none"
        }
        return value.take(12)
    }

    private fun isTrustedSignedEntry(entryId: String): Boolean {
        return config.trustedSignedTextureEntries.any { it.equals(entryId, ignoreCase = true) }
    }

    private fun describeRestoreSkipReason(
        source: ProfileSkinSource?,
        shouldForceRestoreSignedTextures: Boolean
    ): String {
        if (source == null) {
            return if (shouldForceRestoreSignedTextures) {
                "missing source for untrusted signed textures"
            } else {
                "missing source"
            }
        }
        return if (shouldForceRestoreSignedTextures) {
            "untrusted signed textures without restore path"
        } else {
            "restoreUnsignedTextures disabled"
        }
    }

    private fun logSaveResult(
        result: SaveResult,
        profileId: UUID,
        source: ProfileSkinSource?,
        sourceHash: String?,
        reason: String
    ) {
        debug {
            "[ProfileSkinFlow] preprocess cache save: profile=$profileId, result=$result, reason=$reason, source=${describeSource(source)}, sourceHash=${shortHash(sourceHash)}"
        }
    }
}


