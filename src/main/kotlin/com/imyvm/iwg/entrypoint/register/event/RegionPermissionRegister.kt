package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.region.permission.*
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback

fun registerRegionPermissions() {
    playerBuildPermission()
    playerBreakPermission()
    playerBucketUsePermission()
    playerBucketScoopEntityPermission()
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        playerContainerInteraction(player, world, hand, hitResult)
    }
    LazyTicker.registerTask { server ->
        managePlayersFly(server)
    }

    ServerTickEvents.END_SERVER_TICK.register{ server ->
        for (player in getOnlinePlayers(server)) {
            val currentTick = server.overworld().gameTime.toInt()
            processFallImmunity(player, currentTick)
        }
    }
    playerInteractionPermission()
    playerRedstonePermission()
    playerTradePermission()
    playerPvpPermission()
    playerAnimalKillingPermission()
    playerVillagerKillingPermission()
    playerEggUsePermission()
    playerSnowballUsePermission()
    playerPotionUsePermission()
    playerFarmingPermission()
    playerIgnitePermission()
    playerArmorStandPermission()
    playerItemFramePermission()
    playerWindChargeUsePermission()
    playerBowShootPermission()
    playerVehicleUsePermission()
    playerEatingPermission()
    playerFishingPermission()
}
