package icu.h2l.login.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class MiscConfig {
    @Comment("开启 debug 模式")
    @JvmField
    val debug: Boolean = true

    @Comment("是否启用替换 GameProfile")
    @JvmField
    val enableReplaceGameProfile: Boolean = true

    @Comment("不给服务器发送 CHAT_SESSION_UPDATE包")
    @JvmField
    val killChatSession: Boolean = true
}

