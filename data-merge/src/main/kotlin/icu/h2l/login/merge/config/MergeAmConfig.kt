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

package icu.h2l.login.merge.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class MergeAmConfig {
    @Comment("数据库配置")
    var source: SourceConfig = SourceConfig()

    @Comment("表配置")
    var tables: SourceTables = SourceTables()

    @ConfigSerializable
    class SourceConfig {
        @Comment("库类型，支持 SQLITE 或 MYSQL")
        var type: String = "SQLITE"

        @Comment("SQLite 配置")
        var sqlite: SqliteConfig = SqliteConfig()

        @Comment("MySQL 配置")
        var mysql: MysqlConfig = MysqlConfig()
    }

    @ConfigSerializable
    class SqliteConfig {
        @Comment("可选：直接指定 JDBC URL。留空时按 path + parameters 生成")
        var jdbcUrl: String = ""

        @Comment("SQLite 文件路径（相对于插件数据目录）")
        var path: String = "data-merge/authme.db"

        @Comment("可选：JDBC 附加参数")
        var parameters: String = ""
    }

    @ConfigSerializable
    class MysqlConfig {
        @Comment("地址")
        var host: String = "127.0.0.1"

        @Comment("端口")
        var port: Int = 3306

        @Comment("库名")
        var database: String = "authme"

        @Comment("用户名")
        var username: String = "root"

        @Comment("密码")
        var password: String = "password"

        @Comment("可选：JDBC 附加参数")
        var parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
    }

    @ConfigSerializable
    class SourceTables {
        @Comment("表名")
        var authMeTable: String = "authme"
    }
}
