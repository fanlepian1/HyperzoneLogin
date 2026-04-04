package icu.h2l.api.limbo

import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.SimpleCommand

/**
 * Adapter interface for Limbo functionality used by the project.
 * This keeps the API module free of a compile-time dependency on the
 * Limbo third-party API; implementations (in the main plugin) can bridge
 * to the real Limbo implementation when available.
 */
interface HyperZoneLimboAdapter {
    fun registerCommand(meta: CommandMeta, command: SimpleCommand)
}

interface HyperZoneLimboProvider {
    /**
     * Returns the limbo adapter if available, or null when Limbo is not present.
     */
    val limboAdapter: HyperZoneLimboAdapter?
}
