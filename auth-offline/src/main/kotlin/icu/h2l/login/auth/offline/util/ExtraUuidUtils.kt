package icu.h2l.login.auth.offline.util

import icu.h2l.login.auth.offline.config.OfflineMatchConfigLoader
import icu.h2l.login.auth.offline.type.OfflineUUIDType
import icu.h2l.login.auth.offline.util.uuid.PCL2UUIDUtil
import java.nio.charset.StandardCharsets
import java.util.*

object ExtraUuidUtils {
    private val zero: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    fun matchType(holderUUID: UUID?, name: String): OfflineUUIDType {
        if (holderUUID == null) {
            return OfflineUUIDType.ZERO
        }

        val cfg = OfflineMatchConfigLoader.getConfig()
        return when {
            cfg.uuidMatch.offline && holderUUID == getNormalOfflineUUID(name) -> OfflineUUIDType.OFFLINE
            cfg.uuidMatch.pcl2.enable && PCL2UUIDUtil.isPCL2UUID(holderUUID, name) -> OfflineUUIDType.PCL
            cfg.uuidMatch.zero && holderUUID == zero -> OfflineUUIDType.ZERO
            else -> OfflineUUIDType.UNKNOWN
        }
     }

    fun getNormalOfflineUUID(username: String): UUID {
         return UUID.nameUUIDFromBytes(("OfflinePlayer:$username").toByteArray(StandardCharsets.UTF_8))
     }
 }


