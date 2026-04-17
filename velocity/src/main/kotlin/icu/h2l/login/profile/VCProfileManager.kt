/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package icu.h2l.login.profile

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.server.VelocityRegisteredServer
import icu.h2l.api.db.Profile
import icu.h2l.api.event.profile.ProfileAttachedEvent
import icu.h2l.login.HyperZoneLoginMain
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.lang.reflect.Field
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

class VCProfileManager(
    private val profileService: VelocityHyperZoneProfileService,
    private val logger: ComponentLogger
) {
    /**
     * 该管理器只面向 `attachProfile` 之后的正式 Profile / 游戏区流程。
     *
     * 禁止把等待区 `/rename`、`/reUUID` 或其他不可信临时态入口直接接到这里，
     * 否则会把等待区高变化临时状态错误外溢到正式身份补偿同步流程。
     */
    @Subscribe
    fun onProfileAttached(event: ProfileAttachedEvent) {
        if (!HyperZoneLoginMain.getMiscConfig().enableReplaceGameProfile) {
            return
        }

        val attachedProfile = profileService.getAttachedProfile(event.hyperZonePlayer) ?: event.profile
        val proxyPlayer = event.hyperZonePlayer.getProxyPlayerOrNull()
        val connectedPlayer = proxyPlayer as? ConnectedPlayer
        if (connectedPlayer == null) {
            logger.warn(
                "VCProfileManager 在 ProfileAttachedEvent 中未找到可用的 ConnectedPlayer: clientOriginal=${event.hyperZonePlayer.clientOriginalName}, profileId=${attachedProfile.id}"
            )
            return
        }

        val proxyServer = HyperZoneLoginMain.getInstance().proxy as? VelocityServer
            ?: run {
                logger.warn("VCProfileManager 未拿到 VelocityServer，跳过 attach 后正式身份同步")
                return
            }

        val isRegisteredInProxy = ReflectionAccess.connectionsByUuid(proxyServer).values.any { it === connectedPlayer }
            || ReflectionAccess.connectionsByName(proxyServer).values.any { it === connectedPlayer }
        val currentGameProfile = connectedPlayer.gameProfile
        val targetGameProfile = resolveRuntimeGameProfile(
            currentGameProfile = currentGameProfile,
            attachedProfile = attachedProfile,
            enableNameHotChange = HyperZoneLoginMain.getMiscConfig().enableNameHotChange,
            enableUuidHotChange = HyperZoneLoginMain.getMiscConfig().enableUuidHotChange
        )
        if (currentGameProfile == targetGameProfile) {
            return
        }

        if (!isRegisteredInProxy) {
            runCatching {
                applyPreRegistrationProfileSwap(connectedPlayer, targetGameProfile)
            }.onFailure { throwable ->
                logger.error(
                    "VCProfileManager 为未注册的 ConnectedPlayer 应用 attach 后正式身份失败: player=${connectedPlayer.username}, profileId=${attachedProfile.id}, reason=${throwable.message}",
                    throwable
                )
            }
            return
        }

        runCatching {
            applyRuntimeProfileSwap(connectedPlayer, currentGameProfile, targetGameProfile)
        }.onFailure { throwable ->
            logger.error(
                "VCProfileManager 应用 attach 后的正式身份失败: player=${connectedPlayer.username}, profileId=${attachedProfile.id}, reason=${throwable.message}",
                throwable
            )
            return
        }

        if (currentGameProfile.id != targetGameProfile.id) {
            warnSignedIdentityRisk(connectedPlayer, currentGameProfile.id, targetGameProfile.id)
        }
    }

    private fun applyRuntimeProfileSwap(
        player: ConnectedPlayer,
        oldGameProfile: GameProfile,
        newGameProfile: GameProfile
    ) {
        val proxyServer = HyperZoneLoginMain.getInstance().proxy as? VelocityServer
            ?: throw IllegalStateException("当前代理实例不是 VelocityServer，无法执行 VC 热改")
        executeOnPlayerEventLoop(player) {
            ProfileCompensationSupport.applyCompensatingSync(
                connection = player,
                newNameLower = newGameProfile.name.lowercase(Locale.US),
                oldUuid = oldGameProfile.id,
                newUuid = newGameProfile.id,
                connectionsByName = ReflectionAccess.connectionsByName(proxyServer),
                connectionsByUuid = ReflectionAccess.connectionsByUuid(proxyServer),
                serverPlayers = proxyServer.allServers
                    .mapNotNull { it as? VelocityRegisteredServer }
                    .map { ReflectionAccess.players(it) },
                replaceProfile = { ReflectionAccess.profileField.set(player, newGameProfile) },
                rollbackProfile = { ReflectionAccess.profileField.set(player, oldGameProfile) }
            )
        }
    }

    private fun applyPreRegistrationProfileSwap(
        player: ConnectedPlayer,
        newGameProfile: GameProfile,
    ) {
        executeOnPlayerEventLoop(player) {
            ReflectionAccess.profileField.set(player, newGameProfile)
        }
    }

    private fun warnSignedIdentityRisk(player: ConnectedPlayer, oldUuid: UUID, newUuid: UUID) {
        if (player.protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
            return
        }
        val signatureHolder = player.identifiedKey?.signatureHolder
        if (signatureHolder != null && signatureHolder != newUuid) {
            logger.warn(
                "VCProfileManager 检测到 UUID 热改后的签名持有者不一致: player=${player.username}, oldUuid=$oldUuid, newUuid=$newUuid, signatureHolder=$signatureHolder"
            )
        } else {
            logger.warn(
                "VCProfileManager 已执行 UUID 热改: player=${player.username}, oldUuid=$oldUuid, newUuid=$newUuid。"
            )
        }
    }

    private fun <T> executeOnPlayerEventLoop(player: ConnectedPlayer, action: () -> T): T {
        val eventLoop = player.connection.eventLoop()
        if (eventLoop.inEventLoop()) {
            return action()
        }

        val future = CompletableFuture<T>()
        eventLoop.execute {
            runCatching(action)
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally)
        }
        return future.join()
    }

    private object ReflectionAccess {
        val profileField: Field = ConnectedPlayer::class.java.getDeclaredField("profile").apply {
            isAccessible = true
        }
        private val connectionsByNameField: Field = VelocityServer::class.java.getDeclaredField("connectionsByName").apply {
            isAccessible = true
        }
        private val connectionsByUuidField: Field = VelocityServer::class.java.getDeclaredField("connectionsByUuid").apply {
            isAccessible = true
        }
        private val registeredServerPlayersField: Field = VelocityRegisteredServer::class.java.getDeclaredField("players").apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        fun connectionsByName(server: VelocityServer): MutableMap<String, ConnectedPlayer> {
            return connectionsByNameField.get(server) as MutableMap<String, ConnectedPlayer>
        }

        @Suppress("UNCHECKED_CAST")
        fun connectionsByUuid(server: VelocityServer): MutableMap<UUID, ConnectedPlayer> {
            return connectionsByUuidField.get(server) as MutableMap<UUID, ConnectedPlayer>
        }

        @Suppress("UNCHECKED_CAST")
        fun players(server: VelocityRegisteredServer): MutableMap<UUID, ConnectedPlayer> {
            return registeredServerPlayersField.get(server) as MutableMap<UUID, ConnectedPlayer>
        }
    }
}

internal fun resolveRuntimeGameProfile(
    currentGameProfile: GameProfile,
    attachedProfile: Profile,
    enableNameHotChange: Boolean,
    enableUuidHotChange: Boolean
): GameProfile {
    val resolvedName = if (enableNameHotChange) attachedProfile.name else currentGameProfile.name
    val resolvedUuid = if (enableUuidHotChange) attachedProfile.uuid else currentGameProfile.id
    return GameProfile(resolvedUuid, resolvedName, currentGameProfile.properties)
}

internal object ProfileCompensationSupport {
    fun <T> applyCompensatingSync(
        connection: T,
        newNameLower: String,
        oldUuid: UUID,
        newUuid: UUID,
        connectionsByName: MutableMap<String, T>,
        connectionsByUuid: MutableMap<UUID, T>,
        serverPlayers: List<MutableMap<UUID, T>>,
        replaceProfile: () -> Unit,
        rollbackProfile: () -> Unit,
        postSyncHook: (() -> Unit)? = null
    ) {
        val nameSnapshot = LinkedHashMap(connectionsByName)
        val uuidSnapshot = LinkedHashMap(connectionsByUuid)
        val serverSnapshots = serverPlayers.map { LinkedHashMap(it) }

        try {
            validateConflicts(connection, newNameLower, newUuid, connectionsByName, connectionsByUuid, serverPlayers)
            replaceProfile()
            rewriteNameIndex(connection, newNameLower, connectionsByName)
            rewriteUuidIndex(connection, newUuid, connectionsByUuid)
            if (oldUuid != newUuid) {
                rewriteServerPlayers(connection, newUuid, serverPlayers)
            }
            postSyncHook?.invoke()
        } catch (throwable: Throwable) {
            restoreMap(connectionsByName, nameSnapshot)
            restoreMap(connectionsByUuid, uuidSnapshot)
            serverPlayers.zip(serverSnapshots).forEach { (target, snapshot) ->
                restoreMap(target, snapshot)
            }
            runCatching(rollbackProfile).onFailure(throwable::addSuppressed)
            throw throwable
        }
    }

    private fun <T> validateConflicts(
        connection: T,
        newNameLower: String,
        newUuid: UUID,
        connectionsByName: MutableMap<String, T>,
        connectionsByUuid: MutableMap<UUID, T>,
        serverPlayers: List<MutableMap<UUID, T>>
    ) {
        val existingByName = connectionsByName[newNameLower]
        if (existingByName != null && existingByName !== connection) {
            throw IllegalStateException("目标名称已被其他在线连接占用: $newNameLower")
        }

        val existingByUuid = connectionsByUuid[newUuid]
        if (existingByUuid != null && existingByUuid !== connection) {
            throw IllegalStateException("目标 UUID 已被其他在线连接占用: $newUuid")
        }

        serverPlayers.forEach { players ->
            val existing = players[newUuid]
            if (existing != null && existing !== connection) {
                throw IllegalStateException("目标 UUID 已被其他后端服在线连接占用: $newUuid")
            }
        }
    }

    private fun <T> rewriteNameIndex(connection: T, newNameLower: String, connectionsByName: MutableMap<String, T>) {
        connectionsByName.entries.removeIf { it.value === connection && it.key != newNameLower }
        connectionsByName[newNameLower] = connection
    }

    private fun <T> rewriteUuidIndex(connection: T, newUuid: UUID, connectionsByUuid: MutableMap<UUID, T>) {
        connectionsByUuid.entries.removeIf { it.value === connection && it.key != newUuid }
        connectionsByUuid[newUuid] = connection
    }

    private fun <T> rewriteServerPlayers(connection: T, newUuid: UUID, serverPlayers: List<MutableMap<UUID, T>>) {
        serverPlayers.forEach { players ->
            val hasConnection = players.values.any { it === connection }
            if (!hasConnection) {
                return@forEach
            }
            players.entries.removeIf { it.value === connection && it.key != newUuid }
            players[newUuid] = connection
        }
    }

    private fun <K, V> restoreMap(target: MutableMap<K, V>, snapshot: Map<K, V>) {
        target.clear()
        target.putAll(snapshot)
    }
}




