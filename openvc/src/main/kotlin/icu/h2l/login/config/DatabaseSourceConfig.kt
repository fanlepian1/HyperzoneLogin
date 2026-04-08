package icu.h2l.login.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class DatabaseSourceConfig {

    @Comment(
        """数据库类型
        支持的值: SQLITE, MYSQL, MARIADB, H2"""
    )
    val type: String = "SQLITE"

    @Comment("SQLite 数据库配置")
    val sqlite: SQLiteConfig = SQLiteConfig()

    @Comment("MySQL 数据库配置")
    val mysql: MySQLConfig = MySQLConfig()

    @Comment("MariaDB 数据库配置")
    val mariadb: MariaDBConfig = MariaDBConfig()

    @Comment("H2 数据库配置（用于测试）")
    val h2: H2Config = H2Config()

    @Comment("数据库表前缀")
    val tablePrefix: String = "hz_"

    @Comment("连接池配置")
    val pool: PoolConfig = PoolConfig()

    @ConfigSerializable
    class SQLiteConfig {
        @Comment("数据库文件路径（相对于插件数据目录）")
        val path: String = "data/hyperzone_login.db"
    }

    @ConfigSerializable
    class MySQLConfig {
        @Comment("MySQL 服务器地址")
        val host: String = "localhost"

        @Comment("MySQL 服务器端口")
        val port: Int = 3306

        @Comment("数据库名称")
        val database: String = "hyperzone_login"

        @Comment("用户名")
        val username: String = "root"

        @Comment("密码")
        val password: String = "password"

        @Comment("额外的连接参数")
        val parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"

        @Comment("JDBC 驱动类")
        val driverClassName: String = "com.mysql.cj.jdbc.Driver"
    }

    @ConfigSerializable
    class MariaDBConfig {
        @Comment("MariaDB 服务器地址")
        val host: String = "localhost"

        @Comment("MariaDB 服务器端口")
        val port: Int = 3306

        @Comment("数据库名称")
        val database: String = "hyperzone_login"

        @Comment("用户名")
        val username: String = "root"

        @Comment("密码")
        val password: String = "password"

        @Comment("额外的连接参数")
        val parameters: String = "useSSL=false&characterEncoding=utf8"

        @Comment("JDBC 驱动类")
        val driverClassName: String = "org.mariadb.jdbc.Driver"
    }

    @ConfigSerializable
    class H2Config {
        @Comment("H2 数据库文件路径（相对于插件数据目录）")
        val path: String = "data/hyperzone_login"
    }

    @ConfigSerializable
    class PoolConfig {
        @Comment("连接池最大连接数")
        val maximumPoolSize: Int = 10

        @Comment("连接池最小空闲连接数")
        val minimumIdle: Int = 2

        @Comment("连接超时时间（毫秒）")
        val connectionTimeout: Long = 30000

        @Comment("空闲连接超时时间（毫秒）")
        val idleTimeout: Long = 600000

        @Comment("连接最大生命周期（毫秒）")
        val maxLifetime: Long = 1800000
    }
}
