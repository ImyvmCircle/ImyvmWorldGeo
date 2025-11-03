package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.application.event.updateGeographicActionBarForPlayer
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerLocationDisplay() {
    ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        updateGeographicActionBarForPlayer(handler.player)
    }

    LazyTicker.registerTask { server ->
        getOnlinePlayers(server).forEach { player ->
            updateGeographicActionBarForPlayer(player)
        }
    }
}
