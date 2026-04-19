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
class ModulesConfig {
    @Comment("Floodgate 认证；仅在已安装 floodgate 时生效")
    val authFloodgate: Boolean = true

    @Comment("离线认证")
    val authOffline: Boolean = true

    @Comment("Yggdrasil 认证")
    val authYggd: Boolean = true

    @Comment("安全防护")
    val safe: Boolean = true

    @Comment("皮肤缓存")
    val profileSkin: Boolean = true

    @Comment("数据迁移")
    val dataMerge: Boolean = false
}

