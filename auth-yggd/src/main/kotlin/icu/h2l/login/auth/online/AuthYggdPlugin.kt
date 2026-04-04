package icu.h2l.login.auth.online

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.HyperZoneLoginMain

@Plugin(id = "hzl-auth-yggd", name = "HyperZoneLogin - Auth Yggdrasil")
class AuthYggdPlugin @Inject constructor(private val server: ProxyServer) {
    private val logger = java.util.logging.Logger.getLogger("hzl-auth-yggd")
    @Subscribe
    fun onEnable(@Suppress("UNUSED_PARAMETER") e: ProxyInitializeEvent) {
        val mainPluginPresent = server.pluginManager.getPlugin("hyperzonelogin").isPresent
        if (mainPluginPresent) {
            try {
                val main = HyperZoneLoginMain.getInstance()
                main.registerModule(YggdrasilSubModule())
            } catch (t: Throwable) {
                logger.warning("Failed to register YggdrasilSubModule: ${t.message}")
            }
        } else {
            logger.warning("HyperZoneLogin main plugin not found; YggdrasilSubModule will wait until available.")
        }
    }
}


