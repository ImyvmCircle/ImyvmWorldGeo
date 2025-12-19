package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
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

    return if (geoScope.certificateTeleportPoint(playerExecutor.world, teleportPoint)) {
        geoScope.teleportPoint = BlockPos(x, y, z)
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.added", x, y, z, geoScope.scopeName, targetRegion))
        1
    } else {
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.invalid", x, y, z, geoScope.teleportPoint, targetRegion))
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

    return if (teleportPoint != null) {
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
        1
    } else {
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.null",
            geoScope.scopeName,
            targetRegion.name))
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