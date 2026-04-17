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

package icu.h2l.login.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class BackendServerConfig {
    @Comment("等待区实现模式：backend / outpre")
    val vServerMode: String = "backend"

    @Comment("backend 模式使用的真实认证等待服 Velocity 服务器名；留空表示禁用 backend 模式等待服")
    val fallbackAuthServer: String = "lobby"

    @Comment("backend 模式下，认证完成后优先进入的子服务器；若为空或找不到该服务器，则继续按其他候选顺序选择")
    val postAuthDefaultServer: String = "play"

    @Comment("backend 模式下，在真实服务器认证等待区内，如果玩家尝试前往其他服务器，是否记住新的目标并在认证成功后自动连接")
    val rememberRequestedServerDuringAuth: Boolean = true

    @Comment("backend 模式专用：是否启用等待区 UpsertPlayerInfo/TabList 兼容过滤补偿；outpre 不应依赖该补偿")
    val enableWaitingAreaPlayerInfoCompensation: Boolean = true

    @Comment("backend 模式专用：是否启用 attach 后的在线 GameProfile 补偿同步；outpre 应在交付给 Velocity 前自行完成最终 Profile 挂载")
    val enableRuntimeProfileCompensation: Boolean = true
}

