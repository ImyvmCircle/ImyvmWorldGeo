package com.imyvm.iwg.util.translator

import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

fun getUUIDFromPlayerName(server: MinecraftServer, playerName: String): UUID? {
    return server.playerList.getPlayer(playerName)?.uuid
}

fun resolvePlayerName(server: MinecraftServer, uuid: UUID?): String {
    if (uuid == null) return "?"
    return server.playerList.getPlayer(uuid)?.scoreboardName ?: uuid.toString()
}

fun getPlayerByName(server: MinecraftServer, playerName: String): ServerPlayer? {
    return server.playerList.getPlayer(playerName)
}

fun getPlayerByUuid(server: MinecraftServer, playerUuid: UUID): ServerPlayer? {
    return server.playerList.getPlayer(playerUuid)
}

fun getPlayerProfileByName(server: MinecraftServer, playerName: String): GameProfile? {
    return server.playerList.getPlayer(playerName)?.gameProfile
}

fun getPlayerProfileByUuid(server: MinecraftServer, playerUuid: UUID): GameProfile? {
    return server.playerList.getPlayer(playerUuid)?.gameProfile
}

fun getOnlinePlayers(server: MinecraftServer): List<ServerPlayer> {
    return server.playerList.players
}
