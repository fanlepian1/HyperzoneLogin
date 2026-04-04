package icu.h2l.login.auth.offline

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.HyperZoneLoginMain

@Plugin(id = "hzl-auth-offline", name = "HyperZoneLogin - Auth Offline")
class AuthOfflinePlugin @Inject constructor(private val server: ProxyServer) {
    private val logger = java.util.logging.Logger.getLogger("hzl-auth-offline")
    @Subscribe
    fun onEnable(@Suppress("UNUSED_PARAMETER") e: ProxyInitializeEvent) {
        // Prefer direct API usage: call the main plugin's registerModule method.
        // Check plugin presence first (soft-dependency) then invoke the main plugin API directly.
        val mainPluginPresent = server.pluginManager.getPlugin("hyperzonelogin").isPresent
        if (mainPluginPresent) {
            try {
                val main = HyperZoneLoginMain.getInstance()
                main.registerModule(OfflineSubModule())
            } catch (t: Throwable) {
                logger.warning("Failed to register OfflineSubModule: ${t.message}")
            }
        } else {
            logger.warning("HyperZoneLogin main plugin not found; OfflineSubModule will wait until available.")
        }
    }
}


