package icu.h2l.login.manager

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.command.HyperChatCommandManager
import icu.h2l.api.command.HyperChatCommandRegistration
import icu.h2l.api.limbo.HyperZoneLimboAdapter
import net.kyori.adventure.text.Component
import java.util.concurrent.ConcurrentHashMap

object HyperChatCommandManagerImpl : HyperChatCommandManager {
    private val commands = ConcurrentHashMap<String, HyperChatCommandRegistration>()
    @Volatile
    private var limboAdapter: HyperZoneLimboAdapter? = null
    @Volatile
    private var proxyServer: ProxyServer? = null

    fun bindLimbo(proxy: ProxyServer, adapter: HyperZoneLimboAdapter?) {
        proxyServer = proxy
        limboAdapter = adapter
        getRegisteredCommands().forEach { registerToLimbo(it) }
    }

    override fun register(registration: HyperChatCommandRegistration) {
        commands[registration.name.lowercase()] = registration
        registration.aliases.forEach { alias ->
            commands[alias.lowercase()] = registration
        }
        registerToLimbo(registration)
    }

    override fun unregister(name: String) {
        val registration = commands[name.lowercase()] ?: return
        commands.entries.removeIf { (_, value) -> value === registration }
    }

    override fun executeChat(source: CommandSource, chat: String): Boolean {
        val input = chat.trim()
        if (!input.startsWith("/")) return false

        val body = input.substring(1).trim()
        if (body.isEmpty()) return false

        val parts = body.split(Regex("\\s+"))
        val label = parts.first().lowercase()
        val args = if (parts.size > 1) parts.drop(1).toTypedArray() else emptyArray()

        val registration = commands[label] ?: return false
        val invocation = ChatInvocation(source, label, args)
        if (!registration.command.hasPermission(invocation)) {
            source.sendMessage(Component.text("§c没有权限"))
            return true
        }

        registration.command.execute(invocation)
        return true
    }

    override fun getRegisteredCommands(): Collection<HyperChatCommandRegistration> {
        return commands.values.toSet()
    }

    private fun registerToLimbo(registration: HyperChatCommandRegistration) {
        val proxy = proxyServer ?: return
        val authServer = limboAdapter ?: return

        val metaBuilder = proxy.commandManager.metaBuilder(registration.name)
        if (registration.aliases.isNotEmpty()) {
            metaBuilder.aliases(*registration.aliases.toTypedArray())
        }

        authServer.registerCommand(metaBuilder.build(), registration.command)
    }

    private data class ChatInvocation(
        private val source: CommandSource,
        private val alias: String,
        private val args: Array<String>
    ) : SimpleCommand.Invocation {
        override fun source(): CommandSource = source
        override fun arguments(): Array<String> = args
        override fun alias(): String = alias
    }
}
