package com.imyvm.iwg.domain

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.server.MinecraftServer
import java.util.*

class PlayerRegionChecker {
    private val playerRegionScopeMap: MutableMap<UUID, Pair<Region, Region.Companion.GeoScope>?> = mutableMapOf()

    fun updatePlayerRegions(server: MinecraftServer) {
        val onlinePlayers = server.playerManager.playerList

        for (player in onlinePlayers) {
            val playerX = player.blockX
            val playerZ = player.blockZ
            val currentRegionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(playerX, playerZ)
            playerRegionScopeMap[player.uuid] = currentRegionAndScope
        }

        val onlineUUIDs = onlinePlayers.map { it.uuid }.toSet()
        playerRegionScopeMap.keys.retainAll(onlineUUIDs)
    }

    fun getAllRegionScopesWithPlayers(): Map<UUID, Pair<Region?, Region.Companion.GeoScope?>> {
        return playerRegionScopeMap.mapValues { entry ->
            val region = entry.value?.first
            val scope = entry.value?.second
            Pair(region, scope)
        }
    }

}
