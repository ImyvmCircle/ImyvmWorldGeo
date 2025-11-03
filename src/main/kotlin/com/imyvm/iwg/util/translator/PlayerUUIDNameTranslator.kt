package com.imyvm.iwg.util.translator

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun getUUIDFromPlayerName(server: MinecraftServer, playerName: String): UUID? {
    return try {
        server.userCache?.findByName(playerName)?.orElse(null)?.id
    } catch (_: Exception) {
        null
    }
}

fun resolvePlayerName(server: MinecraftServer, uuid: UUID?): String {
    if (uuid == null) return "?"
    return server.userCache?.getByUuid(uuid)?.get()?.name ?: uuid.toString()
}

fun getPlayerByName(server: MinecraftServer, playerName: String): ServerPlayerEntity? {
    return server.playerManager.getPlayer(playerName)?: server.userCache?.findByName(playerName)?.get()?.let {
        server.playerManager.getPlayer(it.id)
    }
}

fun getPlayerByUuid(server: MinecraftServer, playerUuid: UUID): ServerPlayerEntity? {
    return server.userCache?.getByUuid(playerUuid)?.get()?.let {
        server.playerManager.getPlayer(it.id)
    }
}

fun getOnlinePlayers(server: MinecraftServer): List<ServerPlayerEntity> {
    return server.playerManager.playerList
}