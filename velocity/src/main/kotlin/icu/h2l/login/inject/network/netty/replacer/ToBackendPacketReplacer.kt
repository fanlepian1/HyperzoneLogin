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

package icu.h2l.login.inject.network.netty.replacer

import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.config.PlayerInfoForwarding
import com.velocitypowered.proxy.config.VelocityConfiguration
import com.velocitypowered.proxy.connection.ConnectionTypes
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.PlayerDataForwarding
import com.velocitypowered.proxy.connection.PlayerDataForwarding.createBungeeGuardForwardingAddress
import com.velocitypowered.proxy.connection.PlayerDataForwarding.createLegacyForwardingAddress
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeConstants
import com.velocitypowered.proxy.connection.forge.modern.ModernForgeConnectionType
import com.velocitypowered.proxy.protocol.ProtocolUtils
import com.velocitypowered.proxy.protocol.packet.HandshakePacket
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket
import icu.h2l.api.log.debug
import icu.h2l.api.log.error
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.inject.network.ChatSessionUpdatePacketIdResolver
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.player.ProfileSkinApplySupport
import icu.h2l.login.vServer.outpre.OutPreBackendBridge
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress
import java.util.function.Supplier

class ToBackendPacketReplacer(
    private val channel: Channel
) : ChannelOutboundHandlerAdapter() {
    companion object {
        private const val MODERN_FORWARDING_SIGNATURE_LENGTH = 32
    }

    private lateinit var mcConnection: MinecraftConnection
    private lateinit var player: ConnectedPlayer
    private lateinit var hyperPlayer: HyperZonePlayer
    private lateinit var targetServerName: String
    private lateinit var targetServerAddress: InetSocketAddress

    private lateinit var config: VelocityConfiguration

    private fun replaceMessage(
        msg: Any?
    ): Any? {
        val offlinePlayer = !hyperPlayer.isOnlinePlayer

//        离线没有这部分逻辑
//        偷吃点东西 chat_session_update "AdaptivePoolingAllocator$AdaptiveByteBuf(ridx: 0, widx: 323, cap: 323)"，
//        偷吃完可以retire
        if (HyperZoneLoginMain.getMiscConfig().killChatSession) {
            if (msg is ByteBuf) {
                val packetID = readPacketId(msg)
                packetID?.let {
                    if (ChatSessionUpdatePacketIdResolver.isChatSessionUpdate(player.protocolVersion, it)) {
//                        吃掉就结束了
                        retire()
                        return null
                    }
                }
                return msg
            }
        }

        if (!HyperZoneLoginMain.getMiscConfig().enableReplaceGameProfile) {
            if (msg is LoginPluginResponsePacket && (!HyperZoneLoginMain.getMiscConfig().killChatSession || offlinePlayer)) {
                retire()
            }
            return msg
        }
        if (msg is HandshakePacket && shouldRewriteHandshake()) {
            return genHandshake()
        }
        if (msg is ServerLoginPacket) {
            val forwarded = genServerLogin()
            return forwarded
        }
        if (msg is LoginPluginResponsePacket) {
//            如果不需要吃ChatSession，或者离线玩家不会发该包，正常这里就是最后一个关键包
            if (!HyperZoneLoginMain.getMiscConfig().killChatSession || offlinePlayer)
                retire()
            return genLoginPluginResponse(msg)
        }
        return msg
    }

    private fun readPacketId(msg: ByteBuf): Int? {
        if (!msg.isReadable) {
            return null
        }

        val duplicate = msg.duplicate()
        return runCatching {
            ProtocolUtils.readVarInt(duplicate)
        }.getOrNull()
    }

    private fun retire() {
        channel.pipeline().remove(this)
    }

    private fun isLoginServerTarget(): Boolean {
        val loginServerName = HyperZoneLoginMain.getBackendServerConfig().fallbackAuthServer.trim()
        if (loginServerName.isBlank()) {
            return false
        }

        return targetServerName.equals(loginServerName, ignoreCase = true)
    }

    private fun isOutPreAuthTarget(): Boolean {
        if (!isLoginServerTarget()) {
            return false
        }

        return HyperZoneLoginMain.getBackendServerConfig().vServerMode.trim().equals("outpre", ignoreCase = true)
    }

    private fun shouldRewriteHandshake(): Boolean {
        return isOutPreAuthTarget()
    }

    private fun genLoginPluginResponse(
        msg: LoginPluginResponsePacket
    ): LoginPluginResponsePacket {
        if (config.playerInfoForwardingMode == PlayerInfoForwarding.MODERN) {
            val buf = msg.content()
            val requestedForwardingVersion = resolveRequestedForwardingVersion(buf)
            val forwardedProfile = resolveForwardingGameProfile()
            val forwardingData = PlayerDataForwarding.createForwardingData(
                config.forwardingSecret,
                getPlayerRemoteAddressAsString(),
                player.protocolVersion,
                forwardedProfile,
                player.identifiedKey,
                requestedForwardingVersion
            )

            val response = LoginPluginResponsePacket(
                msg.id, true, forwardingData
            )
            return response
        }

        return msg

    }

    private fun resolveRequestedForwardingVersion(content: ByteBuf?): Int {
        if (content == null) {
            debug {
                "[ProfileSkinFlow] modern forwarding version missing content, fallback=${PlayerDataForwarding.MODERN_DEFAULT}, target=$targetServerName"
            }
            return PlayerDataForwarding.MODERN_DEFAULT
        }

        val readableBytes = content.readableBytes()
        if (readableBytes <= MODERN_FORWARDING_SIGNATURE_LENGTH) {
            debug {
                "[ProfileSkinFlow] modern forwarding version payload too short, fallback=${PlayerDataForwarding.MODERN_DEFAULT}, readableBytes=$readableBytes, target=$targetServerName"
            }
            return PlayerDataForwarding.MODERN_DEFAULT
        }

        val duplicate = content.duplicate()
        duplicate.skipBytes(MODERN_FORWARDING_SIGNATURE_LENGTH)
        return runCatching {
            ProtocolUtils.readVarInt(duplicate)
        }.onFailure { throwable ->
            debug {
                "[ProfileSkinFlow] modern forwarding version decode failed, fallback=${PlayerDataForwarding.MODERN_DEFAULT}, readableBytes=$readableBytes, target=$targetServerName, reason=${throwable.message}"
            }
        }.getOrDefault(PlayerDataForwarding.MODERN_DEFAULT)
    }


    private lateinit var fillAddr: InetSocketAddress

    private fun resolvePresentedHostAndPort(): Pair<String, Int> {
        val defaultAddress = player.virtualHost.orElseGet(Supplier { fillAddr })
        if (!isOutPreAuthTarget()) {
            return defaultAddress.hostString to defaultAddress.port
        }

        val cfg = HyperZoneLoginMain.getBackendServerConfig()
        return when (cfg.outPreAddressMode.trim().lowercase()) {
            "backend-address" -> fillAddr.hostString to fillAddr.port
            "custom" -> {
                val host = cfg.outPreAddressHost.trim().ifBlank { fillAddr.hostString }
                val port = if (cfg.outPreAddressPort > 0) cfg.outPreAddressPort else fillAddr.port
                host to port
            }

            else -> defaultAddress.hostString to defaultAddress.port
        }
    }

    private fun genHandshake(): HandshakePacket {
        val forwardingMode: PlayerInfoForwarding? = config.playerInfoForwardingMode
        val protocolVersion: ProtocolVersion? = player.connection.protocolVersion
        val (playerVhost, playerPort) = resolvePresentedHostAndPort()

        val handshake = HandshakePacket()
        handshake.setIntent(HandshakeIntent.LOGIN)
        handshake.protocolVersion = protocolVersion
        if (forwardingMode == PlayerInfoForwarding.LEGACY) {
            handshake.serverAddress = createLegacyForwardingAddress()
        } else if (forwardingMode == PlayerInfoForwarding.BUNGEEGUARD) {
            val secret: ByteArray = config.forwardingSecret
            handshake.serverAddress = createBungeeGuardForwardingAddress(secret)
        } else if (player.connection.type === ConnectionTypes.LEGACY_FORGE) {
            handshake.serverAddress = playerVhost + LegacyForgeConstants.HANDSHAKE_HOSTNAME_TOKEN
        } else if (player.connection.type is ModernForgeConnectionType) {
            handshake.serverAddress = playerVhost + (player
                .connection.type as ModernForgeConnectionType).getModernToken()
        } else {
            handshake.serverAddress = playerVhost
        }

        handshake.port = playerPort
        return handshake
    }

    private fun createLegacyForwardingAddress(): String {
        val (host, _) = resolvePresentedHostAndPort()
        return createLegacyForwardingAddress(
            host,
            getPlayerRemoteAddressAsString(),
            resolveForwardingGameProfile()
        )
    }

    private fun createBungeeGuardForwardingAddress(forwardingSecret: ByteArray): String {
        val (host, _) = resolvePresentedHostAndPort()
        return createBungeeGuardForwardingAddress(
            host,
            getPlayerRemoteAddressAsString(),
            resolveForwardingGameProfile(),
            forwardingSecret
        )
    }

    fun resolveForwardingGameProfile(): GameProfile {
        val loginServerTarget = isLoginServerTarget()
        if (loginServerTarget || hyperPlayer.isInWaitingArea()) {
            return hyperPlayer.getTemporaryGameProfile()
        }

        return requireNotNull(ProfileSkinApplySupport.apply(hyperPlayer)) {
            "Formal profile is unavailable while resolving forwarding game profile for clientOriginal=${hyperPlayer.clientOriginalName}"
        }
    }

    fun getPlayerRemoteAddressAsString(): String {
        val addr: String = resolvePresentedPlayerIp()
        val ipv6ScopeIdx = addr.indexOf('%')
        if (ipv6ScopeIdx == -1) {
            return addr
        } else {
            return addr.substring(0, ipv6ScopeIdx)
        }
    }

    private fun resolvePresentedPlayerIp(): String {
        if (!isOutPreAuthTarget()) {
            return player.remoteAddress.address.hostAddress
        }

        val cfg = HyperZoneLoginMain.getBackendServerConfig()
        return when (cfg.outPrePlayerIpMode.trim().lowercase()) {
            "proxy" -> (mcConnection.channel.localAddress() as? InetSocketAddress)
                ?.address
                ?.hostAddress
                ?: player.remoteAddress.address.hostAddress

            "custom" -> cfg.outPrePlayerIpValue.trim().ifBlank { player.remoteAddress.address.hostAddress }
            else -> player.remoteAddress.address.hostAddress
        }
    }


    private fun genServerLogin(): ServerLoginPacket {
        val loginServerTarget = isLoginServerTarget()
        val isInWaitingArea = hyperPlayer.isInWaitingArea()
        val loginProfile = if (loginServerTarget || isInWaitingArea) {
            hyperPlayer.getTemporaryGameProfile()
        } else {
            hyperPlayer.getAttachedGameProfile()
        }
        if (player.identifiedKey == null
            && player.protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)
        ) {
            return ServerLoginPacket(loginProfile.name, loginProfile.id)
        } else {
            return ServerLoginPacket(
                loginProfile.name,
                player.identifiedKey
            )
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        try {
            initFields(ctx)


//        println("W: $msg")
            val getMsg = replaceMessage(msg)
            if (getMsg == null) {
                ReferenceCountUtil.safeRelease(msg)
                promise?.setSuccess()
                return
            }
            super.write(ctx, getMsg, promise)
        } catch (t: Throwable) {
            // Log via the global API logger bridge so it's consistent with the rest of the project
            error(t) { "ToBackendPacketReplacer write failed: ${t.message}" }
            // Propagate to Netty's exception handling to avoid silently swallowing
            try {
                ctx.fireExceptionCaught(t)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun initFields(ctx: ChannelHandlerContext) {
        if (::mcConnection.isInitialized) {
            return
        }
        val conn = ctx.channel().pipeline().get(MinecraftConnection::class.java) ?: return

        this.mcConnection = conn
        when (val association = conn.association) {
            is VelocityServerConnection -> {
                this.player = association.player
                this.targetServerName = association.server.serverInfo.name
                this.targetServerAddress = association.server.serverInfo.address
            }

            is OutPreBackendBridge -> {
                this.player = association.player
                this.targetServerName = association.targetServerName()
                this.targetServerAddress = association.targetAddress()
            }

            else -> return
        }

        this.fillAddr = targetServerAddress
        this.hyperPlayer = HyperZonePlayerManager.getByPlayer(player)
        val server = HyperZoneLoginMain.getInstance().proxy
        config = server.configuration as VelocityConfiguration
    }

}