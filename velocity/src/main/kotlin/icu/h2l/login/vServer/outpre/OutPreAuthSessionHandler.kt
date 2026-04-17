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

package icu.h2l.login.vServer.outpre

import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.proxy.crypto.IdentifiedKey
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.api.util.UuidUtils
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.config.PlayerInfoForwarding
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.MinecraftSessionHandler
import com.velocitypowered.proxy.connection.client.AuthSessionHandler
import com.velocitypowered.proxy.connection.client.ClientConfigSessionHandler
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl
import com.velocitypowered.proxy.protocol.StateRegistry
import com.velocitypowered.proxy.protocol.packet.LoginAcknowledgedPacket
import com.velocitypowered.proxy.protocol.packet.ServerLoginSuccessPacket
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket
import com.velocitypowered.proxy.protocol.packet.SetCompressionPacket
import icu.h2l.login.inject.network.NettyReflectionHelper
import icu.h2l.login.inject.network.NettyReflectionHelper.reflectedCleanup
import icu.h2l.login.inject.network.NettyReflectionHelper.reflectedDelegatedConnection
import icu.h2l.login.inject.network.NettyReflectionHelper.reflectedTeardown
import io.netty.buffer.ByteBuf
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import java.util.Objects
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * outpre 模式的登录完成处理器。
 *
 * 它保留 Velocity 在 GameProfileRequest 之后构造 `ConnectedPlayer` 的时机，
 * 但把“正常初始服选择 + PostLogin”延后到等待服认证完成之后。
 */
class OutPreAuthSessionHandler(
    private val server: VelocityServer,
    private val inbound: LoginInboundConnection,
    initialProfile: GameProfile,
    private val onlineMode: Boolean,
    private val serverIdHash: String,
    private val outPre: OutPreVServerAuth,
) : MinecraftSessionHandler {
    companion object {
        private val logger: Logger = LogManager.getLogger(AuthSessionHandler::class.java)
    }

    private val mcConnection: MinecraftConnection = inbound.reflectedDelegatedConnection()
    private var profile: GameProfile = initialProfile
    private var connectedPlayer: ConnectedPlayer? = null
    private var loginState = State.START

    override fun activated() {
        profile = mcConnection.type.addGameProfileTokensIfRequired(
            profile,
            server.configuration.playerInfoForwardingMode
        )
        val profileRequestEvent = GameProfileRequestEvent(inbound, profile, onlineMode)
        val finalProfile = profile

        server.eventManager.fire(profileRequestEvent).thenComposeAsync({ profileEvent ->
            if (mcConnection.isClosed) {
                return@thenComposeAsync CompletableFuture.completedFuture(null)
            }

            val player = NettyReflectionHelper.createConnectedPlayer(
                server = server,
                inbound = inbound,
                profile = profileEvent.gameProfile,
                onlineMode = onlineMode,
            )
            connectedPlayer = player
            if (!server.canRegisterConnection(player)) {
                player.disconnect0(
                    Component.translatable("velocity.error.already-connected-proxy", NamedTextColor.RED),
                    true,
                )
                return@thenComposeAsync CompletableFuture.completedFuture(null)
            }

            if (server.configuration.isLogPlayerConnections) {
                logger.info("{} has connected", player)
            }

            server.eventManager.fire(
                PermissionsSetupEvent(player, NettyReflectionHelper.defaultPermissions())
            ).thenAcceptAsync({ event ->
                if (!mcConnection.isClosed) {
                    val function: PermissionFunction? = event.createFunction(player)
                    if (function == null) {
                        logger.error(
                            "A plugin permission provider {} provided an invalid permission function for player {}. Falling back to the default permission function.",
                            event.provider.javaClass.name,
                            player.username,
                        )
                    } else {
                        NettyReflectionHelper.setPermissionFunction(player, function)
                    }
                    startLoginCompletion(player)
                }
            }, mcConnection.eventLoop())
        }, mcConnection.eventLoop()).exceptionally { ex ->
            logger.error("Exception during connection of {}", finalProfile, ex)
            null
        }
    }

    private fun startLoginCompletion(player: ConnectedPlayer) {
        val threshold = server.configuration.compressionThreshold
        if (threshold >= 0 && mcConnection.protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
            mcConnection.write(SetCompressionPacket(threshold))
            mcConnection.setCompressionThreshold(threshold)
        }

        var playerUniqueId: UUID = player.uniqueId
        if (server.configuration.playerInfoForwardingMode == PlayerInfoForwarding.NONE) {
            playerUniqueId = UuidUtils.generateOfflinePlayerUuid(player.username)
        }

        validateIdentifiedKey(player, playerUniqueId)
        completeProxyLoginAndEnterOutPre(player, playerUniqueId)
    }

    private fun validateIdentifiedKey(player: ConnectedPlayer, playerUniqueId: UUID) {
        val playerKey: IdentifiedKey = player.identifiedKey ?: return
        if (playerKey.signatureHolder == null) {
            if (playerKey is IdentifiedKeyImpl) {
                if (!playerKey.internalAddHolder(player.uniqueId)) {
                    if (onlineMode) {
                        inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key"))
                    } else {
                        logger.warn("Key for player {} could not be verified!", player.username)
                    }
                }
            } else {
                logger.warn("A custom key type has been set for player {}", player.username)
            }
            return
        }

        if (!Objects.equals(playerKey.signatureHolder, playerUniqueId)) {
            logger.warn(
                "UUID for Player {} mismatches! Chat/Commands signatures will not work correctly for this player!",
                player.username,
            )
        }
    }

    private fun completeProxyLoginAndEnterOutPre(player: ConnectedPlayer, playerUniqueId: UUID) {
        mcConnection.setAssociation(player)

        server.eventManager.fire(LoginEvent(player, serverIdHash)).thenAcceptAsync({ event ->
            if (mcConnection.isClosed) {
                server.eventManager.fireAndForget(
                    DisconnectEvent(player, DisconnectEvent.LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE)
                )
                return@thenAcceptAsync
            }

            val reason: Optional<Component> = event.result.reasonComponent
            if (reason.isPresent) {
                player.disconnect0(reason.get(), true)
                return@thenAcceptAsync
            }

            if (!server.registerConnection(player)) {
                player.disconnect0(Component.translatable("velocity.error.already-connected-proxy"), true)
                return@thenAcceptAsync
            }

            val success = ServerLoginSuccessPacket()
            success.username = player.username
            success.properties = player.gameProfileProperties
            success.uuid = playerUniqueId
            mcConnection.write(success)

            loginState = State.SUCCESS_SENT
            if (inbound.protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
                loginState = State.ACKNOWLEDGED
                mcConnection.setActiveSessionHandler(
                    StateRegistry.PLAY,
                    NettyReflectionHelper.createInitialConnectSessionHandler(player, server),
                )
                outPre.beginInitialJoin(player)
            }
        }, mcConnection.eventLoop()).exceptionally { ex ->
            logger.error("Exception while completing outpre login phase for {}", player, ex)
            null
        }
    }

    override fun handle(packet: LoginAcknowledgedPacket): Boolean {
        if (loginState != State.SUCCESS_SENT) {
            inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_data"))
            return true
        }

        loginState = State.ACKNOWLEDGED
        val player = connectedPlayer ?: return true
        mcConnection.setActiveSessionHandler(StateRegistry.CONFIG, ClientConfigSessionHandler(server, player))
        outPre.beginInitialJoin(player)
        return true
    }

    override fun handle(packet: ServerboundCookieResponsePacket): Boolean {
        val player = connectedPlayer ?: return true
        server.eventManager.fire(
            CookieReceiveEvent(player, packet.key, packet.payload)
        ).thenAcceptAsync({ event ->
            if (event.result.isAllowed) {
                throw IllegalStateException(
                    "A cookie was requested by a proxy plugin in login phase but the response wasn't handled"
                )
            }
        }, mcConnection.eventLoop())

        return true
    }

    override fun handleUnknown(buf: ByteBuf) {
        mcConnection.close(true)
    }

    override fun disconnected() {
        connectedPlayer?.reflectedTeardown()
        inbound.reflectedCleanup()
    }

    private enum class State {
        START,
        SUCCESS_SENT,
        ACKNOWLEDGED,
    }
}


