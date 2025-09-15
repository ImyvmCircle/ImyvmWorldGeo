package com.imyvm.iwg.inter.register

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.util.ui.updateGeographicActionBarForPlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerLocationDisplay() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        val player = handler.player
        updateGeographicActionBarForPlayer(player)
    }
    ServerTickEvents.END_SERVER_TICK.register { server ->
        ImyvmWorldGeo.tickCounter++
        ImyvmWorldGeo.playerRegionChecker.tick(server)
        server.playerManager.playerList.forEach { player ->
            updateGeographicActionBarForPlayer(player)
        }
    }
}