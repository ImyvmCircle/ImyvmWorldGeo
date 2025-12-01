package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onAddTeleportPoint(
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
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.added", scopeName, x, y, z))
            1
        } else {
            playerExecutor.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.invalid", scopeName))
            0
        }

    } catch (e: IllegalArgumentException) {
        playerExecutor.sendMessage(Translator.tr(e.message))
        return 0
    }
}

fun onRemoveTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    TODO()
}