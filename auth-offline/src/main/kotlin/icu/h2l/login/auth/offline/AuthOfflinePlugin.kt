package icu.h2l.login.auth.offline

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer

@Plugin(id = "hzl-auth-offline", name = "HyperZoneLogin - Auth Offline")
class AuthOfflinePlugin @Inject constructor(private val server: ProxyServer) {
    private val logger = java.util.logging.Logger.getLogger("hzl-auth-offline")
    @Subscribe
    fun onEnable(@Suppress("UNUSED_PARAMETER") e: ProxyInitializeEvent) {
        // Try to find the main plugin instance and register module via reflection
        val mainInstance = server.pluginManager.getPlugin("hyperzonelogin")
            .flatMap { it.instance }
            .orElse(null)

        if (mainInstance != null) {
            try {
                val method = mainInstance.javaClass.methods.firstOrNull {
                    it.name == "registerModule" && it.parameterCount == 1
                }
                if (method != null) {
                    method.invoke(mainInstance, OfflineSubModule())
                } else {
                        logger.warning("HyperZoneLogin main plugin found but registerModule method not present")
                }
            } catch (t: Throwable) {
                        logger.warning("Failed to register OfflineSubModule: ${t.message}")
            }
        } else {
                    logger.warning("HyperZoneLogin main plugin not found; OfflineSubModule will wait until available.")
        }
    }
}


