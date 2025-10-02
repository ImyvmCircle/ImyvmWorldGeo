package com.imyvm.iwg.inter.register

import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.application.ui.updateGeographicActionBarForPlayer
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerLocationDisplay() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        updateGeographicActionBarForPlayer(handler.player)
    }

    LazyTicker.registerTask { server ->
        server.playerManager.playerList.forEach { player ->
            updateGeographicActionBarForPlayer(player)
        }
    }
}
