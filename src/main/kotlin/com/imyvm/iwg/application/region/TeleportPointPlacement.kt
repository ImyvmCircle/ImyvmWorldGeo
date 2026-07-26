package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.infra.config.requireTeleportFallbackSearchRadius
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.shapes.CollisionContext

internal fun findClosestMatchingBlockPos(
    center: BlockPos,
    searchRadius: Int,
    matches: (BlockPos) -> Boolean
): BlockPos? {
    val radius = requireTeleportFallbackSearchRadius(searchRadius)
    return BlockPos.findClosestMatch(center, radius, radius, matches).orElse(null)
}

internal fun resolveScopeWorld(scope: GeoScope, server: MinecraftServer): ServerLevel? {
    val registryKey = ResourceKey.create(Registries.DIMENSION, scope.worldId)
    return server.getLevel(registryKey)
}

internal fun isValidScopeTeleportPoint(
    scope: GeoScope,
    world: Level,
    point: BlockPos?
): Boolean {
    val shape = scope.geoShape ?: return false
    return point != null && isValidShapeTeleportPoint(shape, world, point)
}

internal fun scopeTeleportPointInvalidReasonKey(
    scope: GeoScope,
    world: Level,
    point: BlockPos?
): String? {
    if (point == null) return "teleport_point.invalid.null_point"
    val shape = scope.geoShape ?: return "teleport_point.invalid.no_shape"
    return shapeTeleportPointInvalidReasonKey(shape, world, point)
}

internal fun findNearestValidScopeTeleportPoint(
    scope: GeoScope,
    world: Level,
    center: BlockPos,
    searchRadius: Int
): BlockPos? = scope.geoShape?.let {
    findNearestValidShapeTeleportPoint(it, world, center, searchRadius)
}

internal fun generateRepresentativeSurfacePoint(shape: GeoShape, world: Level): BlockPos? {
    val point = shape.typedGeometry.representativePoint() ?: return null
    val topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, point.first, point.second)
    val candidate = BlockPos(point.first, topY, point.second)
    return candidate.takeIf { isValidShapeTeleportPoint(shape, world, it) }
}

internal fun isValidShapeTeleportPoint(shape: GeoShape, world: Level, point: BlockPos): Boolean =
    isValidShapeTeleportPoint(shape, point, isPhysicallySafeTeleportPosition(world, point))

internal fun isValidShapeTeleportPoint(
    shape: GeoShape,
    point: BlockPos,
    physicallySafe: Boolean
): Boolean = physicallySafe && shape.containsPoint(point.x, point.z)

internal fun shapeTeleportPointInvalidReasonKey(
    shape: GeoShape,
    world: Level,
    point: BlockPos
): String? {
    if (!shape.containsPoint(point.x, point.z)) return "teleport_point.invalid.out_of_scope"
    return physicalTeleportSafetyFailureReasonKey(world, point)
}

internal fun findNearestValidShapeTeleportPoint(
    shape: GeoShape,
    world: Level,
    center: BlockPos,
    searchRadius: Int
): BlockPos? = findClosestMatchingBlockPos(center, searchRadius) { candidate ->
    candidate != center && isValidShapeTeleportPoint(shape, world, candidate)
}

internal fun isPhysicallySafeTeleportPosition(world: Level, point: BlockPos): Boolean =
    physicalTeleportSafetyFailureReasonKey(world, point) == null

internal fun physicalTeleportSafetyFailureReasonKey(world: Level, point: BlockPos): String? {
    val feetState = world.getBlockState(point)
    val headState = world.getBlockState(point.above())
    val groundState = world.getBlockState(point.below())
    val context = CollisionContext.empty()

    if (!feetState.fluidState.isEmpty || !headState.fluidState.isEmpty) {
        return "teleport_point.safety.liquid"
    }
    if (!feetState.getCollisionShape(world, point, context).isEmpty) {
        return "teleport_point.safety.feet_blocked"
    }
    if (!headState.getCollisionShape(world, point.above(), context).isEmpty) {
        return "teleport_point.safety.head_blocked"
    }
    if (!groundState.isFaceSturdy(world, point.below(), Direction.UP)) {
        return "teleport_point.safety.no_ground"
    }
    return null
}
