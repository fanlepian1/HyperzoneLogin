package icu.h2l.login.inject.network

import com.velocitypowered.api.network.ProtocolVersion

object ChatSessionUpdatePacketIdResolver {
    fun resolve(protocolVersion: ProtocolVersion): Int? {
        return when {
            protocolVersion == ProtocolVersion.MINECRAFT_1_19_3 -> 0x20

            protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4)
                && protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_20_5) -> 0x06

            protocolVersion == ProtocolVersion.MINECRAFT_1_20_5 -> 0x07

            protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)
                && protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_21_6) -> 0x08

            protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_21_6)
                    && protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_21_11) -> 0x09

            protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_26_1) -> 0x0A
            else -> null
        }
    }

    fun isChatSessionUpdate(protocolVersion: ProtocolVersion, packetId: Int): Boolean {
        return resolve(protocolVersion) == packetId
    }
}

