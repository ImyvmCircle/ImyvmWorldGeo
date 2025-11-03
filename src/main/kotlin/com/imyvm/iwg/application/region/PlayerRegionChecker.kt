package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.server.MinecraftServer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerRegionChecker {
    private val playerRegionScopeMap: MutableMap<UUID, Pair<Region?, GeoScope?>> = ConcurrentHashMap()

    fun updatePlayerRegions(server: MinecraftServer) {
        val onlinePlayers = getOnlinePlayers(server)

        for (player in onlinePlayers) {
            val playerX = player.blockX
            val playerZ = player.blockZ
            val currentRegionAndScope = RegionDatabase.getRegionAndScopeAt(playerX, playerZ)
            playerRegionScopeMap[player.uuid] = currentRegionAndScope ?: Pair(null, null)
        }

        val onlineUUIDs = onlinePlayers.map { it.uuid }.toSet()
        playerRegionScopeMap.keys.retainAll(onlineUUIDs)
    }

    fun getAllRegionScopesWithPlayers(): Map<UUID, Pair<Region?, GeoScope?>> {
        return playerRegionScopeMap.mapValues { entry ->
            val region = entry.value.first
            val scope = entry.value.second
            Pair(region, scope)
        }
    }
}

