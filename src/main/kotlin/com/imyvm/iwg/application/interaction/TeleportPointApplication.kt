package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.WorldGeoConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onAddingTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    geoScope: GeoScope,
    x: Int,
    y: Int,
    z: Int
): Int {
    val teleportPoint = BlockPos(x, y, z)
    val reasonKey = geoScope.getTeleportPointInvalidReasonKey(playerExecutor.world, teleportPoint)

    return if (reasonKey == null) {
        geoScope.teleportPoint = teleportPoint
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.added", x, y, z, geoScope.scopeName, targetRegion.name))
        1
    } else {
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.invalid", x, y, z, geoScope.scopeName, targetRegion.name))
        playerExecutor.sendMessage(Translator.tr(reasonKey, x, y, z))
        0
    }
}

fun onResettingTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    region: Region,
    scope: GeoScope
): Int {
    playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.reset", scope.scopeName, region.name))
    scope.teleportPoint = null
    return 1
}

fun onGettingTeleportPoint(
    geoScope: GeoScope
): BlockPos? {
    return geoScope.teleportPoint
}

fun onTeleportingPlayer(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    geoScope: GeoScope
): Int {
    val targetWorld = geoScope.getWorld(playerExecutor.server) ?: return 0
    val teleportPoint = onGettingTeleportPoint(geoScope)

    if (teleportPoint == null) {
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.null",
            geoScope.scopeName,
            targetRegion.name))
        return 0
    }

    if (GeoShape.isPhysicalSafe(targetWorld, teleportPoint)) {
        playerExecutor.teleport(
            targetWorld,
            teleportPoint.x.toDouble() + 0.5,
            teleportPoint.y.toDouble(),
            teleportPoint.z.toDouble() + 0.5,
            playerExecutor.yaw,
            playerExecutor.pitch
        )
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.teleported",
            geoScope.scopeName,
            targetRegion.name))
        return 1
    }

    val reasonKey = GeoShape.getPhysicalSafetyFailureReasonKey(targetWorld, teleportPoint)
    val searchRadius = WorldGeoConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value
    val fallback = geoScope.findNearestValidTeleportPoint(targetWorld, teleportPoint, searchRadius)

    return if (fallback != null) {
        geoScope.teleportPoint = fallback
        RegionDatabase.save()
        playerExecutor.teleport(
            targetWorld,
            fallback.x.toDouble() + 0.5,
            fallback.y.toDouble(),
            fallback.z.toDouble() + 0.5,
            playerExecutor.yaw,
            playerExecutor.pitch
        )
        playerExecutor.sendMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.unsafe_updated",
            geoScope.scopeName,
            targetRegion.name,
            teleportPoint.x, teleportPoint.y, teleportPoint.z,
            fallback.x, fallback.y, fallback.z
        ))
        if (reasonKey != null) {
            playerExecutor.sendMessage(Translator.tr(reasonKey, teleportPoint.x, teleportPoint.y, teleportPoint.z))
        }
        1
    } else {
        playerExecutor.sendMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.unsafe_no_fallback",
            geoScope.scopeName,
            targetRegion.name,
            teleportPoint.x, teleportPoint.y, teleportPoint.z
        ))
        if (reasonKey != null) {
            playerExecutor.sendMessage(Translator.tr(reasonKey, teleportPoint.x, teleportPoint.y, teleportPoint.z))
        }
        0
    }
}

fun onTogglingTeleportPointAccessibility(
    scope: GeoScope
): Int {
    scope.isTeleportPointPublic = !scope.isTeleportPointPublic
    return 1
}

fun onGettingTeleportPointAccessibility(
    scope: GeoScope
): Boolean {
    return scope.isTeleportPointPublic
}