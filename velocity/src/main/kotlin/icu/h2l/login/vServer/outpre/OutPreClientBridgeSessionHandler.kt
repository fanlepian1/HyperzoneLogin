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

import com.velocitypowered.proxy.connection.MinecraftSessionHandler
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.protocol.MinecraftPacket
import com.velocitypowered.proxy.protocol.ProtocolUtils
import com.velocitypowered.proxy.protocol.StateRegistry
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket
import com.velocitypowered.proxy.protocol.packet.PingIdentifyPacket
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket
import com.velocitypowered.proxy.protocol.packet.ServerboundCustomClickActionPacket
import com.velocitypowered.proxy.protocol.packet.chat.ChatAcknowledgementPacket
import com.velocitypowered.proxy.protocol.packet.chat.PlayerChatCompletionPacket
import com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerChatPacket
import com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerCommandPacket
import com.velocitypowered.proxy.protocol.packet.chat.session.SessionPlayerChatPacket
import com.velocitypowered.proxy.protocol.packet.chat.session.SessionPlayerCommandPacket
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductAcceptPacket
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket
import com.velocitypowered.proxy.protocol.packet.config.KnownPacksPacket
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import io.netty.util.ReferenceCounted
import java.util.ArrayDeque
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil

class OutPreClientBridgeSessionHandler(
    private val player: ConnectedPlayer,
    private val bridge: OutPreBackendBridge,
    private var configMode: Boolean,
) : MinecraftSessionHandler {
    private data class PendingWrite(
        val requiredPhase: OutPreBackendBridge.Phase,
        val write: (com.velocitypowered.proxy.connection.MinecraftConnection) -> Unit,
        val release: () -> Unit = {},
    )

    private val pendingWrites = ArrayDeque<PendingWrite>()
    private var deferredBrandChannel: String? = null
    private var deferredBrandMessage: String? = null
    @Volatile
    private var phaseCallbackRegistered = false

    private fun backend() = bridge.ensureConnected()

    private fun activeClientPhase(): OutPreBackendBridge.Phase {
        return if (configMode) OutPreBackendBridge.Phase.CONFIG else OutPreBackendBridge.Phase.PLAY_READY
    }

    private fun sendOrQueue(
        requiredPhase: OutPreBackendBridge.Phase,
        action: (com.velocitypowered.proxy.connection.MinecraftConnection) -> Unit,
    ) {
        sendOrQueue(PendingWrite(requiredPhase, action))
    }

    private fun sendOrQueuePacket(
        requiredPhase: OutPreBackendBridge.Phase,
        packet: MinecraftPacket,
    ) {
        if (packet is ReferenceCounted) {
            sendOrQueueRetained(
                requiredPhase = requiredPhase,
                writeNow = { connection -> connection.write(ReferenceCountUtil.retain(packet) as MinecraftPacket) },
                retainForQueue = { ReferenceCountUtil.retain(packet) as MinecraftPacket },
                writer = { connection, queuedPacket -> connection.write(queuedPacket as MinecraftPacket) },
            )
            return
        }
        sendOrQueue(requiredPhase) { it.write(packet) }
    }

    private fun sendOrQueueRetained(
        requiredPhase: OutPreBackendBridge.Phase,
        writeNow: (com.velocitypowered.proxy.connection.MinecraftConnection) -> Unit,
        retainForQueue: () -> Any,
        writer: (com.velocitypowered.proxy.connection.MinecraftConnection, Any) -> Unit,
    ) {
        if (bridge.canForwardClientPackets(requiredPhase)) {
            writeNow(backend())
            return
        }

        if (!bridge.canQueueClientPackets()) {
            return
        }

        val retained = retainForQueue()
        enqueuePendingWrite(
            PendingWrite(
                requiredPhase = requiredPhase,
                write = { writer(it, retained) },
                release = { ReferenceCountUtil.safeRelease(retained) },
            )
        )
    }

    private fun sendOrQueue(pendingWrite: PendingWrite) {
        if (bridge.canForwardClientPackets(pendingWrite.requiredPhase)) {
            pendingWrite.write(backend())
            return
        }

        if (!bridge.canQueueClientPackets()) {
            pendingWrite.release()
            return
        }

        enqueuePendingWrite(pendingWrite)
    }

    private fun enqueuePendingWrite(pendingWrite: PendingWrite) {
        synchronized(pendingWrites) {
            pendingWrites += pendingWrite
        }
        ensurePhaseCallback()
    }

    private fun ensurePhaseCallback() {
        if (phaseCallbackRegistered) {
            return
        }
        phaseCallbackRegistered = true
        bridge.addPhaseListener {
            player.connection.eventLoop().execute {
                if (!bridge.isConnected()) {
                    clearPendingWrites()
                    return@execute
                }
                flushPendingWrites()
            }
        }
    }

    private fun flushPendingWrites() {
        val readyWrites = ArrayList<PendingWrite>()
        synchronized(pendingWrites) {
            if (pendingWrites.isEmpty()) {
                return
            }

            val remainingWrites = ArrayDeque<PendingWrite>()
            while (pendingWrites.isNotEmpty()) {
                val next = pendingWrites.removeFirst()
                if (bridge.canForwardClientPackets(next.requiredPhase)) {
                    readyWrites += next
                } else {
                    remainingWrites += next
                }
            }
            pendingWrites += remainingWrites
        }

        if (readyWrites.isEmpty()) {
            return
        }

        val backend = backend()
        readyWrites.forEach { pendingWrite ->
            pendingWrite.write(backend)
        }
    }

    private fun clearPendingWrites() {
        while (true) {
            val next = synchronized(pendingWrites) {
                if (pendingWrites.isEmpty()) null else pendingWrites.removeFirst()
            } ?: return
            next.release()
        }
    }

    private fun captureDeferredBrand(packet: PluginMessagePacket) {
        deferredBrandChannel = packet.channel
        deferredBrandMessage = runCatching {
            PluginMessageUtil.readBrandMessage(packet.content())
        }.getOrNull()
    }

    private fun flushDeferredBrand() {
        val brandChannel = deferredBrandChannel ?: return
        val brandMessage = deferredBrandMessage ?: return
        val brandBuf = Unpooled.buffer()
        ProtocolUtils.writeString(brandBuf, brandMessage)
        sendOrQueuePacket(
            requiredPhase = OutPreBackendBridge.Phase.CONFIG,
            packet = PluginMessagePacket(brandChannel, brandBuf),
        )
        deferredBrandChannel = null
        deferredBrandMessage = null
    }

    fun onBackendFinishUpdate() {
        flushDeferredBrand()
    }

    override fun handle(packet: PluginMessagePacket): Boolean {
        if (configMode && PluginMessageUtil.isMcBrand(packet)) {
            captureDeferredBrand(packet)
            return true
        }
        sendOrQueueRetained(
            requiredPhase = activeClientPhase(),
            writeNow = { it.write(packet.retain()) },
            retainForQueue = { packet.retain() },
            writer = { connection, queuedPacket -> connection.write(queuedPacket as PluginMessagePacket) },
        )
        return true
    }

    override fun handle(packet: KeepAlivePacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: ClientSettingsPacket): Boolean {
        player.setClientSettings(packet)
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: ResourcePackResponsePacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: KnownPacksPacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: ServerboundCookieResponsePacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: ServerboundCustomClickActionPacket): Boolean {
        sendOrQueueRetained(
            requiredPhase = activeClientPhase(),
            writeNow = { it.write(packet.retain()) },
            retainForQueue = { packet.retain() },
            writer = { connection, queuedPacket -> connection.write(queuedPacket as ServerboundCustomClickActionPacket) },
        )
        return true
    }

    override fun handle(packet: CodeOfConductAcceptPacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: PingIdentifyPacket): Boolean {
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handle(packet: KeyedPlayerChatPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: SessionPlayerChatPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: KeyedPlayerCommandPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: SessionPlayerCommandPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: PlayerChatCompletionPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: ChatAcknowledgementPacket): Boolean {
        sendOrQueue(OutPreBackendBridge.Phase.PLAY_READY) { it.write(packet) }
        return true
    }

    override fun handle(packet: LoginPluginResponsePacket): Boolean {
        sendOrQueuePacket(OutPreBackendBridge.Phase.LOGIN, packet)
        return true
    }

    override fun handle(packet: FinishedUpdatePacket): Boolean {
        if (configMode) {
            bridge.completeConfigurationFromClientAck()
            configMode = false
            player.connection.setActiveSessionHandler(StateRegistry.PLAY, this)
            return true
        }
        sendOrQueue(activeClientPhase()) { it.write(packet) }
        return true
    }

    override fun handleGeneric(packet: MinecraftPacket) {
        sendOrQueuePacket(activeClientPhase(), packet)
    }

    override fun handleUnknown(buf: ByteBuf) {
        sendOrQueueRetained(
            requiredPhase = activeClientPhase(),
            writeNow = { it.write(buf.retain()) },
            retainForQueue = { buf.retain() },
            writer = { connection, queuedBuffer -> connection.write(queuedBuffer as ByteBuf) },
        )
    }

    override fun disconnected() {
        clearPendingWrites()
        bridge.disconnect()
    }
}


