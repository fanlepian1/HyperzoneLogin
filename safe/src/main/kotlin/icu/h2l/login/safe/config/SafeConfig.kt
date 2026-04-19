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

package icu.h2l.login.safe.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class SafeConfig {
    @Comment("启用防护")
    val enable = true

    @Comment("全局连接频率限制")
    @JvmField
    val globalRateLimit = RateLimitConfig(maxAttempts = 80, windowSeconds = 10)

    @Comment("同 IP 连接频率限制")
    @JvmField
    val ipRateLimit = RateLimitConfig(maxAttempts = 8, windowSeconds = 10)

    @Comment("同 IP 超阈值后的临时冷却")
    @JvmField
    val ipCooldown = IpCooldownConfig()

    @Comment("自动高峰防护模式")
    @JvmField
    val strictMode = StrictModeConfig()

    @Comment("认证失败联动防护")
    @JvmField
    val authFailure = AuthFailureConfig()

    @Comment("用户名基础校验")
    @JvmField
    val username = UsernameConfig()

    @ConfigSerializable
    class RateLimitConfig(
        @param:Comment("窗口期内最多允许的尝试次数")
        val maxAttempts: Int = 10,
        @param:Comment("窗口期长度（秒）")
        val windowSeconds: Int = 10
    )

    @ConfigSerializable
    class UsernameConfig {
        @Comment("用户名基础校验")
        val enable = true

        @Comment("最短长度")
        val minLength = 3

        @Comment("最长长度")
        val maxLength = 16

        @Comment("用户名不包含首尾空白")
        val denyLeadingOrTrailingWhitespace = true

        @Comment("允许的用户名正则，默认与 Minecraft 传统用户名规则一致")
        val pattern = "^[A-Za-z0-9_]+$"
    }

    @ConfigSerializable
    class IpCooldownConfig {
        @Comment("同 IP 临时冷却")
        val enabled = true

        @Comment("在统计窗口内触发多少次限流后，开始临时封禁")
        val triggerAttempts = 3

        @Comment("统计窗口长度（秒）")
        val windowSeconds = 60

        @Comment("触发后的冷却时长（秒）")
        val cooldownSeconds = 300
    }

    @ConfigSerializable
    class StrictModeConfig {
        @Comment("自动高峰防护模式")
        val enabled = true

        @Comment("全局连接请求在窗口内达到多少次后进入 严格模式")
        val triggerAttempts = 120

        @Comment("统计窗口（秒）")
        val windowSeconds = 15

        @Comment("保持时长（秒）")
        val recoverAfterSeconds = 90

        @Comment("全局限流")
        @JvmField
        val globalRateLimit = RateLimitConfig(maxAttempts = 30, windowSeconds = 10)

        @Comment("同 IP 限流")
        @JvmField
        val ipRateLimit = RateLimitConfig(maxAttempts = 4, windowSeconds = 10)
    }

    @ConfigSerializable
    class AuthFailureConfig {
        @Comment("统一认证失败联动")
        val enabled = true

        @Comment("同一 IP 在统计窗口内累计多少次认证失败后开始冷却")
        val triggerAttempts = 4

        @Comment("认证失败统计窗口（秒）")
        val windowSeconds = 300

        @Comment("触发后的冷却时长（秒）")
        val cooldownSeconds = 600
    }
}


