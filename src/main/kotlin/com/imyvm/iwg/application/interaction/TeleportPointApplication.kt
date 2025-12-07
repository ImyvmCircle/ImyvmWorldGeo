package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onAddingTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String,
    x: Int,
    y: Int,
    z: Int
): Int {
    try {
        val geoScope = targetRegion.getScopeByName(scopeName)
        val teleportPoint = BlockPos(x, y, z)

        return if (geoScope.certificateTeleportPoint(playerExecutor.world, teleportPoint)) {
            geoScope.teleportPoint = BlockPos(x, y, z)
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.added", x, y, z, scopeName, targetRegion))
            1
        } else {
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.invalid", x, y, z, scopeName, targetRegion))
            0
        }

    } catch (e: IllegalArgumentException) {
        playerExecutor.sendMessage(Translator.tr(e.message))
        return 0
    }
}

fun onResettingTeleportPoint(
    scope: GeoScope
): Int {
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
    scopeName: String
): Int {
    return try {
        val scope = targetRegion.getScopeByName(scopeName)
        val targetWorld = scope.getWorld(playerExecutor.server) ?: return 0
        val teleportPoint = onGettingTeleportPoint(scope)

        return if (teleportPoint != null) {
            playerExecutor.teleport(
                targetWorld,
                teleportPoint.x.toDouble() + 0.5,
                teleportPoint.y.toDouble(),
                teleportPoint.z.toDouble() + 0.5,
                playerExecutor.yaw,
                playerExecutor.pitch
            )
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.teleported", scopeName, targetRegion))
            1
        } else {
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.null", scopeName, targetRegion))
            0
        }
    } catch (e: IllegalArgumentException) {
        playerExecutor.sendMessage(Translator.tr(e.message))
        0
    }
}