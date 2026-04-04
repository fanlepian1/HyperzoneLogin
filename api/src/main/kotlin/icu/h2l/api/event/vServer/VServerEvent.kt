package icu.h2l.api.event.vServer

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayer

class VServerEvent(
    val proxyPlayer: Player,
    val hyperZonePlayer: HyperZonePlayer
)
