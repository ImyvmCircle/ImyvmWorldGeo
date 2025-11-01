package com.imyvm.iwg.util.translator

import net.minecraft.server.MinecraftServer
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

fun getPlayerByName(server: MinecraftServer, playerName: String) = server.playerManager.getPlayer(playerName)

fun getPlayerByUuid(server: MinecraftServer, playerUuid: UUID) = server.playerManager.getPlayer(playerUuid)