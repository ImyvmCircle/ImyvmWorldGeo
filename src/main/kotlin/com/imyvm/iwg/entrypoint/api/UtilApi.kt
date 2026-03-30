package com.imyvm.iwg.inter.api

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.isValidName as isValidNameImpl
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.util.translator.*
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

@Suppress("unused")
object UtilApi {
    fun isSelectingPoints(playerExecutor: ServerPlayer): Boolean = ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerExecutor.uuid)
    fun isActionBarEnabled(playerExecutor: ServerPlayer): Boolean = ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(playerExecutor.uuid)
    fun getPlayerUUID(server: MinecraftServer, playerName: String): UUID? {
        return getUUIDFromPlayerName(server, playerName)
    }

    fun getPlayerUUID(player: ServerPlayer, playerName: String): UUID? {
        return getUUIDFromPlayerName(player.level().server, playerName)
    }

    fun getPlayerName(server: MinecraftServer, uuid: UUID?): String {
        return resolvePlayerName(server, uuid)
    }

    fun getPlayerName(player: ServerPlayer, uuid: UUID?): String {
        return resolvePlayerName(player.level().server, uuid)
    }

    fun getPlayer(playerExecutor: ServerPlayer, playerName: String): ServerPlayer? = getPlayerByName(playerExecutor.level().server, playerName)

    fun getPlayer(server: MinecraftServer, playerName: String): ServerPlayer? = getPlayerByName(server, playerName)

    fun getPlayer(playerExecutor: ServerPlayer, playerUuid: UUID): ServerPlayer? = getPlayerByUuid(playerExecutor.level().server, playerUuid)

    fun getPlayer(server: MinecraftServer, playerUuid: UUID): ServerPlayer? = getPlayerByUuid(server, playerUuid)

    fun getPlayerProfile(server: MinecraftServer, playerName: String): GameProfile? = getPlayerProfileByName(server, playerName)

    fun getPlayerProfile(playerExecutor: ServerPlayer, playerName: String): GameProfile? = getPlayerProfileByName(playerExecutor.level().server, playerName)

    fun getPlayerProfile(server: MinecraftServer, playerUuid: UUID): GameProfile? = getPlayerProfileByUuid(server, playerUuid)

    fun getPlayerProfile(playerExecutor: ServerPlayer, playerUuid: UUID): GameProfile? = getPlayerProfileByUuid(playerExecutor.level().server, playerUuid)

    fun parseRegionFoundingTime(regionNumberId: Int): Long {
        return parseFoundingTimeFromRegionId(regionNumberId)
    }

    fun isValidName(name: String): Boolean = isValidNameImpl(name)
}