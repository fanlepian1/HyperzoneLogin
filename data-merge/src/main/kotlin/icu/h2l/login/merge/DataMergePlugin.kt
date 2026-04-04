package icu.h2l.login.merge

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.HyperZoneLoginMain

@Plugin(id = "hzl-data-merge", name = "HyperZoneLogin - Data Merge")
class DataMergePlugin @Inject constructor(private val server: ProxyServer) {
    private val logger = java.util.logging.Logger.getLogger("hzl-data-merge")
    @Subscribe
    fun onEnable(@Suppress("UNUSED_PARAMETER") e: ProxyInitializeEvent) {
        val mainPluginPresent = server.pluginManager.getPlugin("hyperzonelogin").isPresent
        if (mainPluginPresent) {
            try {
                val main = HyperZoneLoginMain.getInstance()
                main.registerModule(MergeSubModule())
            } catch (t: Throwable) {
                logger.warning("Failed to register MergeSubModule: ${t.message}")
            }
        } else {
            logger.warning("HyperZoneLogin main plugin not found; MergeSubModule will wait until available.")
        }
    }
}


