package icu.h2l.login.profile.skin.db

import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.warn
import icu.h2l.api.profile.skin.ProfileSkinSource
import icu.h2l.api.profile.skin.ProfileSkinTextures
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class ProfileSkinCacheRecord(
    val profileId: UUID,
    val sourceHash: String?,
    val skinUrl: String?,
    val skinModel: String?,
    val textures: ProfileSkinTextures,
    val updatedAt: Long
)

class ProfileSkinCacheRepository(
    private val databaseManager: HyperZoneDatabaseManager,
    private val table: ProfileSkinCacheTable
) {
    enum class SaveResult {
        INSERTED,
        UPDATED,
        SKIPPED
    }

    fun findByProfileId(profileId: UUID): ProfileSkinCacheRecord? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.profileId eq profileId }
                .limit(1)
                .map { row ->
                    ProfileSkinCacheRecord(
                        profileId = row[table.profileId],
                        sourceHash = row[table.sourceHash],
                        skinUrl = row[table.skinUrl],
                        skinModel = row[table.skinModel],
                        textures = ProfileSkinTextures(
                            value = row[table.textureValue],
                            signature = row[table.textureSignature]
                        ),
                        updatedAt = row[table.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }

    fun findBySourceHash(sourceHash: String): ProfileSkinCacheRecord? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.sourceHash eq sourceHash }
                .limit(1)
                .map { row ->
                    ProfileSkinCacheRecord(
                        profileId = row[table.profileId],
                        sourceHash = row[table.sourceHash],
                        skinUrl = row[table.skinUrl],
                        skinModel = row[table.skinModel],
                        textures = ProfileSkinTextures(
                            value = row[table.textureValue],
                            signature = row[table.textureSignature]
                        ),
                        updatedAt = row[table.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }

    fun save(profileId: UUID, source: ProfileSkinSource?, textures: ProfileSkinTextures, sourceHash: String?): SaveResult {
        val existing = findByProfileId(profileId)
        if (existing != null && !hasSourceChanged(existing, source, sourceHash)) {
            return SaveResult.SKIPPED
        }

        if (existing == null) {
            return try {
                databaseManager.executeTransaction {
                    table.insert {
                        it[table.profileId] = profileId
                        it[table.sourceHash] = sourceHash
                        it[skinUrl] = source?.skinUrl
                        it[skinModel] = source?.model
                        it[textureValue] = textures.value
                        it[textureSignature] = textures.signature
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                SaveResult.INSERTED
            } catch (e: Exception) {
                warn { "写入皮肤缓存失败: ${e.message}" }
                SaveResult.SKIPPED
            }
        }

        val updated = try {
            databaseManager.executeTransaction {
                table.update({ table.profileId eq profileId }) {
                    it[table.sourceHash] = sourceHash
                    it[skinUrl] = source?.skinUrl
                    it[skinModel] = source?.model
                    it[textureValue] = textures.value
                    it[textureSignature] = textures.signature
                    it[updatedAt] = System.currentTimeMillis()
                }
            } > 0
        } catch (e: Exception) {
            warn { "更新皮肤缓存失败: ${e.message}" }
            false
        }

        if (updated) {
            return SaveResult.UPDATED
        }

        return SaveResult.SKIPPED
    }

    private fun hasSourceChanged(
        existing: ProfileSkinCacheRecord,
        source: ProfileSkinSource?,
        sourceHash: String?
    ): Boolean {
        val newUrl = source?.skinUrl
        val newModel = source?.model

        if (newUrl.isNullOrBlank() && sourceHash.isNullOrBlank()) {
            return false
        }

        if (!sourceHash.isNullOrBlank() && sourceHash != existing.sourceHash) {
            return true
        }

        if (!newUrl.isNullOrBlank() && newUrl != existing.skinUrl) {
            return true
        }

        if (!newModel.isNullOrBlank() && newModel != existing.skinModel) {
            return true
        }

        return false
    }
}

