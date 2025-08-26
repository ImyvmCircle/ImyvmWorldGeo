package com.imyvm.iwg.region

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.server.MinecraftServer
import java.util.*

class PlayerRegionChecker {
    private val playerRegionList: MutableMap<UUID, Region?> = mutableMapOf()

    fun updatePlayerRegions(server: MinecraftServer){
        if ((ImyvmWorldGeo.tickCounter % 20).toInt() != 0) return

        for (player in server.playerManager.playerList) {
            val playerX = player.blockX
            val playerZ = player.blockZ
            val currentRegion = ImyvmWorldGeo.data.getRegionAt(playerX, playerZ)

            playerRegionList[player.uuid] = currentRegion
        }

        val onlinePlayers = server.playerManager.playerList.map { it.uuid }.toSet()
        playerRegionList.keys.retainAll(onlinePlayers)
    }

    fun getRegionForPlayer(uuid: UUID): Region? {
        return playerRegionList[uuid]
    }
}