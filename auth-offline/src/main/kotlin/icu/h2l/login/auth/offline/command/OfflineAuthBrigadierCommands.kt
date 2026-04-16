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

package icu.h2l.login.auth.offline.command

import icu.h2l.api.command.HyperChatBrigadierRegistration

object OfflineAuthBrigadierCommands {
    fun login(): HyperChatBrigadierRegistration {
        return greedyCommand("login")
    }

    fun register(): HyperChatBrigadierRegistration {
        return greedyCommand("register")
    }

    fun changePassword(): HyperChatBrigadierRegistration {
        return greedyCommand("changepassword")
    }

    fun logout(): HyperChatBrigadierRegistration {
        return plainCommand("logout")
    }

    fun unregister(): HyperChatBrigadierRegistration {
        return greedyCommand("unregister")
    }

    fun email(): HyperChatBrigadierRegistration {
        return greedyCommand("email")
    }

    fun totp(): HyperChatBrigadierRegistration {
        return greedyCommand("totp")
    }

    private fun greedyCommand(name: String): HyperChatBrigadierRegistration {
        return HyperChatBrigadierRegistration { context ->
            context.literal(name)
                .executes { commandContext ->
                    context.execute(commandContext.source)
                }
                .then(context.greedyArguments())
        }
    }

    private fun plainCommand(name: String): HyperChatBrigadierRegistration {
        return HyperChatBrigadierRegistration { context ->
            context.literal(name)
                .executes { commandContext ->
                    context.execute(commandContext.source)
                }
        }
    }
}
