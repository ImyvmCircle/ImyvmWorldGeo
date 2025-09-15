package com.imyvm.iwg.domain

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.server.MinecraftServer
import java.util.*

class PlayerRegionChecker {
    private val playerRegionScopeMap: MutableMap<UUID, Pair<Region, Region.Companion.GeoScope>?> = mutableMapOf()

    fun tick(server: MinecraftServer) {
        if ((ImyvmWorldGeo.tickCounter % 20).toInt() != 0) return
        updatePlayerRegions(server)
    }

    private fun updatePlayerRegions(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            val playerX = player.blockX
            val playerZ = player.blockZ
            val currentRegionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(playerX, playerZ)

            playerRegionScopeMap[player.uuid] = currentRegionAndScope
        }

        val onlinePlayers = server.playerManager.playerList.map { it.uuid }.toSet()
        playerRegionScopeMap.keys.retainAll(onlinePlayers)
    }

    fun getAllRegionScopesWithPlayers(): Map<UUID, Pair<Region?, Region.Companion.GeoScope?>> {
        return playerRegionScopeMap.mapValues { entry ->
            val region = entry.value?.first
            val scope = entry.value?.second
            Pair(region, scope)
        }
    }

}
