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

import com.google.common.io.ByteStreams
import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults
import com.velocitypowered.proxy.network.Connections
import com.velocitypowered.proxy.protocol.StateRegistry
import com.velocitypowered.proxy.protocol.packet.HandshakePacket
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket
import com.velocitypowered.proxy.server.VelocityRegisteredServer
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.vServer.outpre.handler.OutPreBackendBridgeSessionHandler
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

//这是Server类型的
class OutPreBackendBridge(
    val proxyServer: VelocityServer,
    private val authTargetAddress: InetSocketAddress,
    player: ConnectedPlayer,
    private val owner: OutPreVServerAuth,
    registeredServer: VelocityRegisteredServer,
    private val outPreServerInfo: ServerInfo
) : VelocityServerConnection(registeredServer, null, player, proxyServer) {
    enum class Phase {
        IDLE,
        CONNECTING,
        LOGIN,
        CONFIG,
        PLAY_READY,
        CLOSING,
        CLOSED,
    }

    var backendConnection: MinecraftConnection? = null
        private set
    private val connectFuture = CompletableFuture<ConnectionRequestResults.Impl?>()
    private val playReadyFuture = CompletableFuture<Void>()
    private val phaseListeners = CopyOnWriteArrayList<(Phase) -> Unit>()

    /** 缓存的 [OutPreRegisteredServer]，始终返回同一实例。 */
    val outPreRegisteredServer: OutPreRegisteredServer =
        OutPreRegisteredServer(proxyServer, outPreServerInfo)

    @Volatile
    private var bridgeSessionHandler: OutPreBackendBridgeSessionHandler? = null

    @Volatile
    private var awaitingClientConfigurationAck = false

    @Volatile
    private var connectStarted = false

    @Volatile
    private var phase = Phase.IDLE

    fun targetAddress(): InetSocketAddress = authTargetAddress

    override fun getPreviousServer(): Optional<RegisteredServer> {
        return player.currentServer.map { it.server }
    }

    override fun getServerInfo(): ServerInfo {
        return outPreServerInfo
    }

    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean {
        val backendConnection = backendConnection ?: return false
        if (backendConnection.isClosed) {
            return false
        }
        backendConnection.write(PluginMessagePacket(identifier.id, Unpooled.wrappedBuffer(data)))
        return true
    }

    override fun sendPluginMessage(identifier: ChannelIdentifier, dataEncoder: PluginMessageEncoder): Boolean {
        val output = ByteStreams.newDataOutput()
        dataEncoder.encode(output)
        return sendPluginMessage(identifier, output.toByteArray())
    }

    override fun connect(): CompletableFuture<ConnectionRequestResults.Impl?> {
        if (connectStarted) {
            return connectFuture
        }
        connectStarted = true
        updatePhase(Phase.CONNECTING)
        outPreRegisteredServer.registerBridge(this)
        proxyServer.createBootstrap(player.connection.eventLoop())
            .handler(proxyServer.backendChannelInitializer)
            .connect(authTargetAddress)
            .addListener(ChannelFutureListener { channelFuture ->
                if (!channelFuture.isSuccess) {
                    fail(channelFuture.cause() ?: IllegalStateException("OutPre backend bootstrap failed"))
                    return@ChannelFutureListener
                }

                val backendConnection = MinecraftConnection(channelFuture.channel(), proxyServer)
                this@OutPreBackendBridge.backendConnection = backendConnection
                updatePhase(Phase.LOGIN)
                backendConnection.setAssociation(this)
                channelFuture.channel().pipeline().addLast(Connections.HANDLER, backendConnection)

                if (!backendConnection.setActiveSessionHandler(StateRegistry.HANDSHAKE)) {
                    val handler = OutPreBackendBridgeSessionHandler(this)
                    bridgeSessionHandler = handler
                    backendConnection.setActiveSessionHandler(StateRegistry.HANDSHAKE, handler)
                    backendConnection.addSessionHandler(StateRegistry.LOGIN, handler)
                    backendConnection.addSessionHandler(StateRegistry.CONFIG, handler)
                    backendConnection.addSessionHandler(StateRegistry.PLAY, handler)
                }
                startHandshake(backendConnection)
            })
        return connectFuture
    }

    override fun getConnection(): MinecraftConnection? {
        return backendConnection
    }

    fun readyFuture(): CompletableFuture<Void> {
        return playReadyFuture
    }

    fun isConnected(): Boolean {
        return backendConnection != null
    }

    fun phase(): Phase {
        return phase
    }

    fun addPhaseListener(listener: (Phase) -> Unit) {
        phaseListeners += listener
        listener(phase)
    }

    fun canForwardClientPackets(requiredPhase: Phase): Boolean {
        val currentPhase = phase
        if (currentPhase == Phase.CLOSING || currentPhase == Phase.CLOSED) {
            return false
        }
        if (backendConnection?.isClosed != false) {
            return false
        }
        return currentPhase.ordinal >= requiredPhase.ordinal
    }

    fun isReadyForForwarding(): Boolean {
        return phase == Phase.PLAY_READY && backendConnection?.isClosed == false
    }

    fun canQueueClientPackets(): Boolean {
        return when (phase) {
            Phase.CONNECTING, Phase.LOGIN, Phase.CONFIG -> true
            else -> false
        }
    }

    private fun startHandshake(connection: MinecraftConnection) {
        val protocolVersion: ProtocolVersion = player.protocolVersion
        val vServerConfig = HyperZoneLoginMain.getCoreConfig().vServer
        val targetAddress = targetAddress()
        val handshake = HandshakePacket()
        handshake.setIntent(HandshakeIntent.LOGIN)
        handshake.protocolVersion = protocolVersion
        handshake.serverAddress = vServerConfig.outpre.resolveOutprePresentedHost(targetAddress)
        handshake.port = vServerConfig.outpre.resolveOutprePresentedPort(targetAddress)
        connection.delayedWrite(handshake)
        connection.protocolVersion = protocolVersion
        connection.setActiveSessionHandler(StateRegistry.LOGIN)
        if (player.identifiedKey == null && player.protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
            connection.delayedWrite(ServerLoginPacket(player.username, player.uniqueId))
        } else {
            connection.delayedWrite(ServerLoginPacket(player.username, player.identifiedKey))
        }
        connection.flush()
    }

    override fun ensureConnected(): MinecraftConnection {
        return backendConnection ?: throw IllegalStateException("OutPre backend bridge is not connected")
    }

    override fun isActive(): Boolean {
        return backendConnection?.isClosed == false && player.isActive
    }

    fun onBackendLoginSucceeded(usesConfigurationPhase: Boolean) {
        awaitingClientConfigurationAck = false
        updatePhase(if (usesConfigurationPhase) Phase.CONFIG else Phase.LOGIN)
        connectFuture.complete(ConnectionRequestResults.successful(server))
    }

    fun markAwaitingClientConfigurationAck() {
        awaitingClientConfigurationAck = true
    }

    fun completeConfigurationFromClientAck() {
        val connection = ensureConnected()
        val handler =
            bridgeSessionHandler ?: throw IllegalStateException("OutPre backend bridge handler is not initialized")
        connection.eventLoop().execute {
            if (!awaitingClientConfigurationAck || connection.isClosed) {
                return@execute
            }
            awaitingClientConfigurationAck = false
            connection.write(FinishedUpdatePacket.INSTANCE)
            connection.setActiveSessionHandler(StateRegistry.PLAY, handler)
        }
    }

    fun onBackendJoined() {
        updatePhase(Phase.PLAY_READY)
        playReadyFuture.complete(null)
        owner.onInitialBridgeJoined(this, player)
    }

    fun onBackendDisconnected(reason: String? = null) {
        val failure = IllegalStateException(reason ?: "OutPre backend bridge disconnected")
        fail(failure, notifyOwner = false)
        owner.onInitialBridgeDisconnected(this, player, reason)
    }

    override fun disconnect() {
        updatePhase(Phase.CLOSING)
        awaitingClientConfigurationAck = false
        if (!connectFuture.isDone) {
            connectFuture.completeExceptionally(IllegalStateException("OutPre backend bridge closed"))
        }
        if (!playReadyFuture.isDone) {
            playReadyFuture.completeExceptionally(IllegalStateException("OutPre backend bridge closed before play ready"))
        }
        backendConnection?.close(false)
        backendConnection = null
        outPreRegisteredServer.unregisterBridge(this)
        updatePhase(Phase.CLOSED)
    }

    private fun fail(throwable: Throwable, notifyOwner: Boolean = false) {
        updatePhase(Phase.CLOSING)
        awaitingClientConfigurationAck = false
        connectFuture.completeExceptionally(throwable)
        playReadyFuture.completeExceptionally(throwable)
        backendConnection?.close(false)
        backendConnection = null
        outPreRegisteredServer.unregisterBridge(this)
        updatePhase(Phase.CLOSED)
        if (notifyOwner) {
            owner.onInitialBridgeDisconnected(this, player, throwable.message)
        }
    }

    private fun updatePhase(newPhase: Phase) {
        val currentPhase = phase
        if (currentPhase == newPhase) {
            return
        }
        if (currentPhase == Phase.CLOSED) {
            return
        }
        if (currentPhase == Phase.CLOSING && newPhase != Phase.CLOSED) {
            return
        }
        if (newPhase != Phase.CLOSING && newPhase != Phase.CLOSED && newPhase.ordinal < currentPhase.ordinal) {
            return
        }
        phase = newPhase
        phaseListeners.forEach { listener ->
            runCatching {
                listener(newPhase)
            }
        }
    }
}
