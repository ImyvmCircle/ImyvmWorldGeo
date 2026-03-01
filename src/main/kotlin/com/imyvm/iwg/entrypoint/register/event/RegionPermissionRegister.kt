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
    registerPlayerInteractionPermission()
    registerPlayerRedstonePermission()
    registerPlayerTradePermission()
    registerPlayerPvpPermission()
    registerPlayerAnimalKillingPermission()
    registerPlayerVillagerKillingPermission()
    registerPlayerEggUsePermission()
    registerPlayerSnowballUsePermission()
    registerPlayerPotionUsePermission()
    registerPlayerFarmingPermission()
    registerPlayerIgnitePermission()
    registerPlayerArmorStandPermission()
    registerPlayerItemFramePermission()
}

fun registerPlayerBuildBreakPermission(){
    playerBuildPermission()
    playerBreakPermission()
    playerBucketUsePermission()
    playerBucketScoopEntityPermission()
}

fun registerPlayerContainerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        playerContainerInteraction(player, world, hand, hitResult)
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

fun registerPlayerInteractionPermission() {
    playerInteractionPermission()
}

fun registerPlayerRedstonePermission() {
    playerRedstonePermission()
}

fun registerPlayerTradePermission() {
    playerTradePermission()
}

fun registerPlayerPvpPermission() {
    playerPvpPermission()
}

fun registerPlayerAnimalKillingPermission() {
    playerAnimalKillingPermission()
}

fun registerPlayerVillagerKillingPermission() {
    playerVillagerKillingPermission()
}

fun registerPlayerEggUsePermission() {
    playerEggUsePermission()
}

fun registerPlayerSnowballUsePermission() {
    playerSnowballUsePermission()
}

fun registerPlayerPotionUsePermission() {
    playerPotionUsePermission()
}

fun registerPlayerFarmingPermission() {
    playerFarmingPermission()
}

fun registerPlayerIgnitePermission() {
    playerIgnitePermission()
}

fun registerPlayerArmorStandPermission() {
    playerArmorStandPermission()
}

fun registerPlayerItemFramePermission() {
    playerItemFramePermission()
}