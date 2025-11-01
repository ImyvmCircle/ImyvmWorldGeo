package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.util.translator.getPlayerByName
import com.imyvm.iwg.util.translator.getPlayerByUuid
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import com.imyvm.iwg.util.translator.resolvePlayerName
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

@Suppress("unused")
object UtilApi {
    fun getPlayerUUID(server: MinecraftServer, playerName: String): UUID? {
        return getUUIDFromPlayerName(server, playerName)
    }

    fun getPlayerUUID(player: ServerPlayerEntity, playerName: String): UUID? {
        return getUUIDFromPlayerName(player.server, playerName)
    }

    fun getPlayerName(server: MinecraftServer, uuid: UUID?): String {
        return resolvePlayerName(server, uuid)
    }

    fun getPlayerName(player: ServerPlayerEntity, uuid: UUID?): String {
        return resolvePlayerName(player.server, uuid)
    }

    fun getPlayer(playerExecutor: ServerPlayerEntity, playerName: String): ServerPlayerEntity? = getPlayerByName(playerExecutor.server, playerName)

    fun getPlayer(server: MinecraftServer, playerName: String): ServerPlayerEntity? = getPlayerByName(server, playerName)

    fun getPlayer(playerExecutor: ServerPlayerEntity, playerUuid: UUID): ServerPlayerEntity? = getPlayerByUuid(playerExecutor.server, playerUuid)

    fun getPlayer(server: MinecraftServer, playerUuid: UUID): ServerPlayerEntity? = getPlayerByUuid(server, playerUuid)

    fun parseRegionFoundingTime(regionNumberId: Int): Long {
        return parseFoundingTimeFromRegionId(regionNumberId)
    }
}