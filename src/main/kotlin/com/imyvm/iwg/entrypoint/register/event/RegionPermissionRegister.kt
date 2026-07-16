package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.region.permission.*
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback

fun registerRegionPermissions() {
    registerManagedFlightRecoveryAttachment()
    playerBuildPermission()
    playerBreakPermission()
    playerBucketUsePermission()
    playerBucketScoopEntityPermission()
    UseBlockCallback.EVENT.register { player, world, _, hitResult ->
        containerInteraction(player, world, hitResult)
    }
    LazyTicker.registerTask { server ->
        managePlayersFly(server)
    }

    ServerTickEvents.END_SERVER_TICK.register{ server ->
        for (player in getOnlinePlayers(server)) {
            val currentTick = server.overworld().gameTime
            processFallImmunity(player, currentTick)
        }
    }
    ServerPlayerEvents.JOIN.register(::processPlayerFly)
    ServerPlayerEvents.LEAVE.register(::handleFlyDisconnect)
    ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, alive ->
        handleFlyRespawn(oldPlayer, newPlayer, alive)
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
