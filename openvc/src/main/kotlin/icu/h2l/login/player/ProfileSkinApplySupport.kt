package icu.h2l.login.player

import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.profile.ProfileSkinApplyEvent
import icu.h2l.api.log.error
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain

object ProfileSkinApplySupport {
    fun apply(hyperZonePlayer: HyperZonePlayer): GameProfile {
        return apply(hyperZonePlayer, hyperZonePlayer.getGameProfile())
    }

    fun apply(hyperZonePlayer: HyperZonePlayer, baseProfile: GameProfile): GameProfile {
        val event = ProfileSkinApplyEvent(hyperZonePlayer, baseProfile)

        runCatching {
            HyperZoneLoginMain.getInstance().proxy.eventManager.fire(event).join()
        }.onFailure { throwable ->
            error(throwable) { "Profile skin apply event failed: ${throwable.message}" }
        }

        val textures = event.textures ?: return baseProfile
        val mergedProperties = baseProfile.properties
            .filterNot { it.name.equals("textures", ignoreCase = true) }
            .toMutableList()
            .apply { add(textures.toProperty()) }

        return GameProfile(
            baseProfile.id,
            baseProfile.name,
            mergedProperties
        )
    }
}


