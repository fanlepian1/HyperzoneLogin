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
    @Comment("等待区实现模式：auto / limbo / backend / outpre")
    val vServerMode: String = "auto"

    @Comment("当未安装 limboapi 时，使用真实后端服务器作为认证等待区；留空表示禁用")
    val fallbackAuthServer: String = "lobby"

    @Comment("登入完成后优先进入的子服务器；若为空或找不到该服务器，则继续按其他候选顺序选择")
    val postAuthDefaultServer: String = "play"

    @Comment("在真实服务器认证等待区内，如果玩家尝试前往其他服务器，是否记住新的目标并在认证成功后自动连接")
    val rememberRequestedServerDuringAuth: Boolean = true
}

