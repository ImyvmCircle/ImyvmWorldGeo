package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoSpaceLevel
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

fun recordPlayerBehavior(
    type: WorldGeoBehaviorType,
    player: ServerPlayer,
    world: Level,
    pos: BlockPos,
    objectId: String? = null,
    targetId: String? = null,
    unixMillis: Long = System.currentTimeMillis()
) {
    WorldGeoBehaviorEventBus.publish(playerBehaviorEvent(type, player, world, pos, objectId, targetId, unixMillis))
}

internal fun playerBehaviorEvent(
    type: WorldGeoBehaviorType,
    player: ServerPlayer,
    world: Level,
    pos: BlockPos,
    objectId: String? = null,
    targetId: String? = null,
    unixMillis: Long = System.currentTimeMillis()
): WorldGeoBehaviorEvent {
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(world, pos.x, pos.z)
    return WorldGeoBehaviorEvent(
        type = type,
        playerUuid = player.uuid,
        playerName = player.scoreboardName,
        dimensionId = world.dimension().identifier(),
        x = pos.x,
        y = pos.y,
        z = pos.z,
        unixMillis = unixMillis,
        regionId = resolved?.first?.numberID,
        regionName = resolved?.first?.name,
        scopeId = resolved?.second?.assignedScopeIdOrNull?.raw,
        scopeName = resolved?.second?.scopeName,
        subSpaceId = resolved?.third?.subSpaceId,
        subSpaceName = resolved?.third?.name,
        objectId = objectId,
        targetId = targetId
    )
}

fun recordBlockBehavior(type: WorldGeoBehaviorType, player: ServerPlayer, world: Level, pos: BlockPos, state: BlockState) {
    recordPlayerBehavior(type, player, world, pos, objectId = BuiltInRegistries.BLOCK.getKey(state.block).toString())
}

internal fun recordSpaceBehavior(
    type: WorldGeoBehaviorType,
    player: ServerPlayer,
    regionName: String,
    regionId: Int,
    scopeName: String?,
    scopeId: Long?,
    subSpaceName: String?,
    subSpaceId: Long?,
    spaceLevel: WorldGeoSpaceLevel,
    unixMillis: Long
) {
    val pos = player.blockPosition()
    WorldGeoBehaviorEventBus.publish(
        WorldGeoBehaviorEvent(
            type = type,
            playerUuid = player.uuid,
            playerName = player.scoreboardName,
            dimensionId = player.level().dimension().identifier(),
            x = pos.x,
            y = pos.y,
            z = pos.z,
            unixMillis = unixMillis,
            regionId = regionId,
            regionName = regionName,
            scopeId = scopeId,
            scopeName = scopeName,
            subSpaceId = subSpaceId,
            subSpaceName = subSpaceName,
            spaceLevel = spaceLevel
        )
    )
}
