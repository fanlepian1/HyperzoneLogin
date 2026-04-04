package icu.h2l.login.player

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.db.Profile
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.util.RemapUtils
import icu.h2l.login.HyperZoneLoginMain
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class OpenVcHyperZonePlayer(
//    最开始客户端传入的，不可信
    override var userName: String,
    override var uuid: UUID,
) : HyperZonePlayer {

    private var proxyPlayer: Player? = null

    @Volatile
    var profileId: UUID? = null

    private val isVerifiedState = AtomicBoolean(false)
    private val hasSpawned = AtomicBoolean(false)
    private val messageQueue = ConcurrentLinkedQueue<Component>()

    @Volatile
    private var limboPlayer: LimboPlayer? = null

    private val databaseHelper = HyperZoneLoginMain.getInstance().databaseHelper

    init {
        profileId = databaseHelper
            .getProfileByNameOrUuid(userName, uuid)
            ?.id
        profileId?.let { _ ->
            val profile = getProfile()
            userName = profile!!.name
            uuid = profile.uuid
        }
    }

    fun update(player: Player) {
        proxyPlayer = player
    }

    fun onSpawn(player: LimboPlayer) {
        limboPlayer = player
        update(player.proxyPlayer)
        hasSpawned.set(true)

        while (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll() ?: continue
            proxyPlayer?.sendMessage(message)
        }
    }

    override fun canRegister(): Boolean {
        return profileId == null
    }

    override fun register(userName: String?, uuid: UUID?): Profile {
        val resolvedName = userName ?: this.userName
        val remapPrefix = HyperZoneLoginMain.getRemapConfig().prefix
        val resolvedUuid = uuid ?: RemapUtils.genUUID(resolvedName, remapPrefix)

        val existing = databaseHelper.getProfileByNameOrUuid(resolvedName, resolvedUuid)
        if (existing != null) {
            throw IllegalStateException("玩家 $resolvedName 已存在 Profile，无法重复注册")
        }

        val profile = Profile(
            id = RemapUtils.genProfileUUID(resolvedName),
            name = resolvedName,
            uuid = resolvedUuid
        )

        val created = databaseHelper.createProfile(profile.id, profile.name, profile.uuid)
        if (!created) {
            throw IllegalStateException("玩家 ${userName} 注册失败，数据库写入失败")
        }

        profileId = profile.id

        return profile
    }

    override fun getProfile(): Profile? {
        val currentProfileId = profileId ?: return null
        return databaseHelper.getProfile(currentProfileId)
    }

    override fun isVerified(): Boolean {
        return isVerifiedState.get()
    }

    override fun canBind(): Boolean {
        return isVerified()
    }

    override fun overVerify() {
        if (isVerifiedState.compareAndSet(false, true)) {
            limboPlayer?.disconnect()
        }
    }

    fun exitLimbo() {
        limboPlayer?.disconnect()
    }

    override fun sendMessage(message: Component) {
        if (hasSpawned.get()) {
            proxyPlayer?.sendMessage(message)
            return
        }

        messageQueue.offer(message)
    }

    override fun getGameProfile(): GameProfile {
        if (!HyperZoneLoginMain.getMiscConfig().enableReplaceGameProfile) {
            return proxyPlayer!!.gameProfile
        }
        val resolvedProfile = getProfile()
        return GameProfile(
            resolvedProfile!!.uuid,
            resolvedProfile.name,
            ArrayList()
        )
    }
}
