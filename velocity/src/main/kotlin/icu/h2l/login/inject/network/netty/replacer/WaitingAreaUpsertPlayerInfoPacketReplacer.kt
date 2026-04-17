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

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import icu.h2l.api.log.debug
import icu.h2l.login.HyperZoneLoginMain
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil

class WaitingAreaUpsertPlayerInfoPacketReplacer : ChannelInboundHandlerAdapter() {
    private var proxyPlayer: Player? = null
    private var unresolvedPlayerLogged = false

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        initPlayer(ctx)

        val player = proxyPlayer
        val isWaitingArea = if (player != null) {
            isPlayerInWaitingArea(player)
        } else {
            null
        }

        if (player == null && msg is UpsertPlayerInfoPacket && !unresolvedPlayerLogged) {
            unresolvedPlayerLogged = true
            debug {
                "[WaitingAreaTabCompat] upsert passthrough before player resolved: channel=${ctx.channel().id().asShortText()}, packet=${describePacket(msg)}"
            }
        }

        if (isWaitingArea == false) {
            val resolvedPlayer = player ?: run {
                super.channelRead(ctx, msg)
                return
            }
            val nextPacketName = msg?.javaClass?.simpleName ?: "null"
            debug {
                "[WaitingAreaTabCompat] retire filter after waiting area: player=${resolvedPlayer.username}, uuid=${resolvedPlayer.uniqueId}, currentServer=${currentServerName(resolvedPlayer)}, nextPacket=$nextPacketName"
            }
            retire(ctx)
            super.channelRead(ctx, msg)
            return
        }

        if (isWaitingArea == true && msg is UpsertPlayerInfoPacket) {
            val resolvedPlayer = player ?: run {
                ReferenceCountUtil.safeRelease(msg)
                return
            }
            debug {
                "[WaitingAreaTabCompat] drop waiting-area upsert: player=${resolvedPlayer.username}, uuid=${resolvedPlayer.uniqueId}, currentServer=${currentServerName(resolvedPlayer)}, packet=${describePacket(msg)}"
            }
            ReferenceCountUtil.safeRelease(msg)
            return
        }

        super.channelRead(ctx, msg)
    }

    private fun initPlayer(ctx: ChannelHandlerContext) {
        if (proxyPlayer != null) {
            return
        }

        val connection = ctx.channel().pipeline().get(MinecraftConnection::class.java) ?: return
        val backendConnection = connection.association as? VelocityServerConnection ?: return
        proxyPlayer = backendConnection.player
        unresolvedPlayerLogged = false
        debug {
            "[WaitingAreaTabCompat] resolved backend player for upsert filter: player=${backendConnection.player.username}, uuid=${backendConnection.player.uniqueId}, target=${backendConnection.server.serverInfo.name}, channel=${ctx.channel().id().asShortText()}"
        }
    }

    private fun isPlayerInWaitingArea(player: Player): Boolean {
        val main = HyperZoneLoginMain.getInstance()
        return main.serverAdapter?.isPlayerInWaitingArea(player) == true
    }

    private fun retire(ctx: ChannelHandlerContext) {
        ctx.pipeline().remove(this)
    }

    private fun currentServerName(player: Player): String {
        return player.currentServer
            .map { it.server.serverInfo.name }
            .orElse("<none>")
    }

    private fun describePacket(packet: UpsertPlayerInfoPacket): String {
        return "actions=${packet.actions}, entries=${packet.entries.size}"
    }
}


