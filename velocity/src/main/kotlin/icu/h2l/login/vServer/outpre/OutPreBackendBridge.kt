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

import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.MinecraftConnectionAssociation
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.network.Connections
import com.velocitypowered.proxy.protocol.StateRegistry
import com.velocitypowered.proxy.protocol.packet.HandshakePacket
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket
import com.velocitypowered.proxy.server.VelocityRegisteredServer
import io.netty.channel.ChannelFutureListener
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class OutPreBackendBridge(
    val proxyServer: VelocityServer,
    val authServer: VelocityRegisteredServer,
    val player: ConnectedPlayer,
    private val owner: OutPreVServerAuth,
) : MinecraftConnectionAssociation {
    enum class Phase {
        IDLE,
        CONNECTING,
        LOGIN,
        CONFIG,
        PLAY_READY,
        CLOSING,
        CLOSED,
    }

    var connection: MinecraftConnection? = null
        private set
    private val connectFuture = CompletableFuture<Void>()
    private val playReadyFuture = CompletableFuture<Void>()
    private val phaseListeners = CopyOnWriteArrayList<(Phase) -> Unit>()
    @Volatile
    private var bridgeSessionHandler: OutPreBackendBridgeSessionHandler? = null
    @Volatile
    private var awaitingClientConfigurationAck = false
    @Volatile
    private var connectStarted = false
    @Volatile
    private var phase = Phase.IDLE

    fun targetServerName(): String = authServer.serverInfo.name

    fun targetAddress(): InetSocketAddress = authServer.serverInfo.address

    fun connect(): CompletableFuture<Void> {
        if (connectStarted) {
            return connectFuture
        }
        connectStarted = true
        updatePhase(Phase.CONNECTING)
        proxyServer.createBootstrap(player.connection.eventLoop())
            .handler(proxyServer.backendChannelInitializer)
            .connect(authServer.serverInfo.address)
            .addListener(ChannelFutureListener { channelFuture ->
                if (!channelFuture.isSuccess) {
                    fail(channelFuture.cause() ?: IllegalStateException("OutPre backend bootstrap failed"))
                    return@ChannelFutureListener
                }

                val backendConnection = MinecraftConnection(channelFuture.channel(), proxyServer)
                connection = backendConnection
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

    fun readyFuture(): CompletableFuture<Void> {
        return playReadyFuture
    }

    fun isConnected(): Boolean {
        return connection != null
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
        if (connection?.isClosed != false) {
            return false
        }
        return currentPhase.ordinal >= requiredPhase.ordinal
    }

    fun isReadyForForwarding(): Boolean {
        return phase == Phase.PLAY_READY && connection?.isClosed == false
    }

    fun canQueueClientPackets(): Boolean {
        return when (phase) {
            Phase.CONNECTING, Phase.LOGIN, Phase.CONFIG -> true
            else -> false
        }
    }

    private fun startHandshake(connection: MinecraftConnection) {
        val protocolVersion: ProtocolVersion = player.protocolVersion
        val handshake = HandshakePacket()
        handshake.setIntent(HandshakeIntent.LOGIN)
        handshake.protocolVersion = protocolVersion
        handshake.serverAddress = player.virtualHost.orElseGet(::targetAddress).hostString
        handshake.port = player.virtualHost.orElseGet(::targetAddress).port
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

    fun ensureConnected(): MinecraftConnection {
        return connection ?: throw IllegalStateException("OutPre backend bridge is not connected")
    }

    fun isActive(): Boolean {
        return connection?.isClosed == false && player.isActive
    }

    fun onBackendLoginSucceeded(usesConfigurationPhase: Boolean) {
        awaitingClientConfigurationAck = false
        updatePhase(if (usesConfigurationPhase) Phase.CONFIG else Phase.LOGIN)
        connectFuture.complete(null)
    }

    fun markAwaitingClientConfigurationAck() {
        awaitingClientConfigurationAck = true
    }

    fun completeConfigurationFromClientAck() {
        val connection = ensureConnected()
        val handler = bridgeSessionHandler ?: throw IllegalStateException("OutPre backend bridge handler is not initialized")
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

    fun disconnect() {
        updatePhase(Phase.CLOSING)
        awaitingClientConfigurationAck = false
        if (!connectFuture.isDone) {
            connectFuture.completeExceptionally(IllegalStateException("OutPre backend bridge closed"))
        }
        if (!playReadyFuture.isDone) {
            playReadyFuture.completeExceptionally(IllegalStateException("OutPre backend bridge closed before play ready"))
        }
        connection?.close(false)
        connection = null
        updatePhase(Phase.CLOSED)
    }

    fun registeredServer(): RegisteredServer = authServer

    private fun fail(throwable: Throwable, notifyOwner: Boolean = false) {
        updatePhase(Phase.CLOSING)
        awaitingClientConfigurationAck = false
        connectFuture.completeExceptionally(throwable)
        playReadyFuture.completeExceptionally(throwable)
        connection?.close(false)
        connection = null
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

