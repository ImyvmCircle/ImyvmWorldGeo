package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.server.MinecraftServer
import java.util.*

object PlayerRegionChecker {
    private val playerRegionScopeMap: MutableMap<UUID, Triple<Region?, GeoScope?, SubSpace?>> = mutableMapOf()

    fun updatePlayerRegions(server: MinecraftServer) {
        val onlinePlayers = getOnlinePlayers(server)

        for (player in onlinePlayers) {
            val playerWorld = player.level()
            val playerX = player.blockPosition().x
            val playerZ = player.blockPosition().z
            val currentRegionAndScope = RegionDatabase.getRegionScopeSubSpaceAt(playerWorld, playerX, playerZ)
            playerRegionScopeMap[player.uuid] = currentRegionAndScope ?: Triple(null, null, null)
        }

        val onlineUUIDs = onlinePlayers.map { it.uuid }.toSet()
        playerRegionScopeMap.keys.retainAll(onlineUUIDs)
    }

    fun getAllRegionScopesWithPlayers(): Map<UUID, Triple<Region?, GeoScope?, SubSpace?>> {
        return playerRegionScopeMap.mapValues { entry ->
            Triple(entry.value.first, entry.value.second, entry.value.third)
        }
    }
}
