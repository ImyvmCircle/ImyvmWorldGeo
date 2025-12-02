package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
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
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    return try {
        val geoScope = targetRegion.getScopeByName(scopeName)
        geoScope.teleportPoint = null
        playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.reset", scopeName, targetRegion))
        1
    } catch (e: IllegalArgumentException) {
        playerExecutor.sendMessage(Translator.tr(e.message))
        0
    }
}

fun onGettingTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): BlockPos? {
    return try {
        val geoScope = targetRegion.getScopeByName(scopeName)
        geoScope.teleportPoint
    } catch (e: IllegalArgumentException) {
        playerExecutor.sendMessage(Translator.tr(e.message))
        null
    }
}

fun onTeleportingPlayer(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    return try {
        val teleportPoint = onGettingTeleportPoint(playerExecutor, targetRegion, scopeName)

        return if (teleportPoint != null) {
            playerExecutor.teleport(teleportPoint.x.toDouble(), teleportPoint.y.toDouble(),
                teleportPoint.z.toDouble(), true)
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