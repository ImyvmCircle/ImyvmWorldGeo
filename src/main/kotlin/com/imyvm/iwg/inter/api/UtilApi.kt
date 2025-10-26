package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
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

    fun parseRegionFoundingTime(regionNumberId: Int): Long {
        return parseFoundingTimeFromRegionId(regionNumberId)
    }
}