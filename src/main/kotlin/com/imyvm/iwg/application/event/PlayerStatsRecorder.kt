package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

fun registerPlayerStatsEvents() {
    ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
        val player = entity as? ServerPlayer ?: return@register
        recordPlayerDeath(player)
    }

    PlayerBlockBreakEvents.AFTER.register { world, player, pos, blockState, _ ->
        val serverPlayer = player as? ServerPlayer ?: return@register
        recordSuccessfulBlockBreak(serverPlayer, world, pos, blockState)
    }
}

fun recordSuccessfulBlockPlacement(player: ServerPlayer, world: Level, pos: BlockPos) {
    recordBlockBehavior(WorldGeoBehaviorType.BLOCK_PLACE, player, world, pos, world.getBlockState(pos))
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return
    RegionDatabase.incrementRegionBlockPlaceStat(regionAndScope.first, player.uuid)
}

private fun recordSuccessfulBlockBreak(player: ServerPlayer, world: Level, pos: BlockPos, blockState: BlockState) {
    recordBlockBehavior(WorldGeoBehaviorType.BLOCK_BREAK, player, world, pos, blockState)
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return
    RegionDatabase.incrementRegionBlockBreakStat(regionAndScope.first, player.uuid)
}

private fun recordPlayerDeath(player: ServerPlayer) {
    val pos = player.blockPosition()
    recordPlayerBehavior(WorldGeoBehaviorType.PLAYER_DEATH, player, player.level(), pos)
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level(), pos.x, pos.z) ?: return
    RegionDatabase.incrementRegionDeathStat(regionAndScope.first, player.uuid)
}
