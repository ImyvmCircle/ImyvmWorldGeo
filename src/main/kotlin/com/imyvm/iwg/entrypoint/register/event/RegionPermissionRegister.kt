package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.region.permission.*
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback

fun registerRegionPermissions() {
    registerPlayerBuildBreakPermission()
    registerPlayerContainerInteractionPermission()
    registerFlyPermission()
}

fun registerPlayerBuildBreakPermission(){
    playerBuildPermission()
    playerBreakPermission()
}

fun registerPlayerContainerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, _, hitResult ->
        playerContainerInteraction(player, world, hitResult)
    }
}

fun registerFlyPermission() {
    LazyTicker.registerTask { server ->
        managePlayersFly(server)
    }

    ServerTickEvents.END_SERVER_TICK.register{ server ->
        for (player in getOnlinePlayers(server)) {
            val currentTick = server.overworld.time.toInt()
            processFallImmunity(player, currentTick)
        }
    }
}