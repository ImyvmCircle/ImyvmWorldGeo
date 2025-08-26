package com.imyvm.iwg.region

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.server.MinecraftServer
import java.util.*

class PlayerRegionChecker {
    private val playerRegionMap: MutableMap<UUID, Region?> = mutableMapOf()
    fun tick(server: MinecraftServer) {
        if ((ImyvmWorldGeo.tickCounter % 20).toInt() != 0) return
        updatePlayerRegions(server)
    }

    private fun updatePlayerRegions(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            val playerX = player.blockX
            val playerZ = player.blockZ
            val currentRegion = ImyvmWorldGeo.data.getRegionAt(playerX, playerZ)

            playerRegionMap[player.uuid] = currentRegion
        }

        val onlinePlayers = server.playerManager.playerList.map { it.uuid }.toSet()
        playerRegionMap.keys.retainAll(onlinePlayers)
    }

    fun getRegionForPlayer(uuid: UUID): Region? {
        return playerRegionMap[uuid]
    }

    fun getAllRegions(): Map<UUID, Region?> {
        return playerRegionMap.toMap()
    }
}
