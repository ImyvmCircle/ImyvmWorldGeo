package com.imyvm.iwg.application.event

import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level

fun registerPlayerStatsEvents() {
    ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
        val player = entity as? ServerPlayer ?: return@register
        recordPlayerDeath(player)
    }

    PlayerBlockBreakEvents.AFTER.register { world, player, pos, _, _ ->
        val serverPlayer = player as? ServerPlayer ?: return@register
        recordSuccessfulBlockBreak(serverPlayer, world, pos)
    }
}

fun recordSuccessfulBlockPlacement(player: ServerPlayer, world: Level, pos: BlockPos) {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return
    RegionDatabase.recordRegionBlockPlace(regionAndScope.first, player.uuid)
}

private fun recordSuccessfulBlockBreak(player: ServerPlayer, world: Level, pos: BlockPos) {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return
    RegionDatabase.recordRegionBlockBreak(regionAndScope.first, player.uuid)
}

private fun recordPlayerDeath(player: ServerPlayer) {
    val pos = player.blockPosition()
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level(), pos.x, pos.z) ?: return
    RegionDatabase.recordRegionDeath(regionAndScope.first, player.uuid)
}
