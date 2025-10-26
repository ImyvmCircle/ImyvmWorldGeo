package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.application.interaction.scope.shape.*
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onModifyScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    try {
        val existingScope = targetRegion.getScopeByName(scopeName)
        val selectedPositions = checkAndGetPlayerPositions(player) ?: return 0
        val shapeType = existingScope.geoShape?.geoShapeType ?: GeoShapeType.UNKNOWN
        if (shapeType == GeoShapeType.UNKNOWN) {
            player.sendMessage(Translator.tr("interaction.meta.scope.modify.unknown_shape_type"))
            return 0
        }

        return when (shapeType) {
            GeoShapeType.POLYGON -> modifyPolygonScope(player, targetRegion, existingScope, selectedPositions)
            GeoShapeType.CIRCLE -> modifyCircleScope(player, targetRegion, existingScope, selectedPositions)
            GeoShapeType.RECTANGLE -> modifyRectangleScope(player, targetRegion, existingScope, selectedPositions)
            else -> 0
        }
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr(e.message))
        return 0
    }
}

private fun checkAndGetPlayerPositions(player: ServerPlayerEntity): MutableList<BlockPos>? {
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("interaction.meta.select.not_in_mode"))
        return null
    }
    return ImyvmWorldGeo.pointSelectingPlayers[playerUUID]
}

private fun modifyPolygonScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
): Int {
    return when (selectedPositions.size) {
        0 -> { player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_insufficient_points")); 0}
        1 -> { modifyScopePolygonMonoPoint(player, targetRegion, existingScope, selectedPositions); 1 }
        2 -> { modifyScopePolygonMove(player, targetRegion, existingScope, selectedPositions); 1 }
        else -> { modifyScopePolygonInsertPoint(player, targetRegion, existingScope, selectedPositions); 1 }
    }
}

private fun modifyCircleScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
): Int {
    if (selectedPositions.size == 1) modifyScopeCircleRadius(player, targetRegion, existingScope, selectedPositions)
    else modifyScopeCircleCenter(player, targetRegion, existingScope, selectedPositions)
    return 1
}

private fun modifyRectangleScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
): Int {
    modifyScopeRectangle(player, targetRegion, existingScope, selectedPositions)
    return 1
}