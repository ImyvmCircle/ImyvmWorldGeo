package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.infra.LazyTicker
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerRegionEntryExit() {
    LazyTicker.registerTask { server ->
        PlayerRegionEntryExitTracker.processTransitions(server)
    }

    ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
        PlayerRegionEntryExitTracker.handleDisconnect(handler.player)
    }
}
