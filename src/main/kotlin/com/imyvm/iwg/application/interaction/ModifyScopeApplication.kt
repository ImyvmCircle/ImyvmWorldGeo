package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.application.interaction.scope.shape.*
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun onModifyScope(
    player: ServerPlayer,
    targetRegion: Region,
    scopeName: String
): Int {
    val existingScope = getScopeOrNotify(player, targetRegion, scopeName) ?: return 0
    val selectedPositions = checkAndGetPlayerPositions(player, targetRegion, existingScope) ?: return 0
    val shapeType = existingScope.geoShape?.geoShapeType ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.unknown_shape_type"))
        return 0
    }

    return when (shapeType) {
        GeoShapeType.POLYGON -> modifyPolygonScope(player, targetRegion, existingScope, selectedPositions)
        GeoShapeType.CIRCLE -> modifyCircleScope(player, targetRegion, existingScope, selectedPositions)
        GeoShapeType.RECTANGLE -> modifyScopeRectangle(player, targetRegion, existingScope, selectedPositions)
    }.let { if (it) 1 else 0 }
}

private fun checkAndGetPlayerPositions(
    player: ServerPlayer,
    targetRegion: Region,
    existingScope: GeoScope
): MutableList<BlockPos>? {
    val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (state == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode"))
        return null
    }
    if (!isModifySelectionFor(state, existingScope)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.modify_target_mismatch"))
        return null
    }
    val error = validateModifySelectionTarget(
        targetRegion,
        existingScope,
        player.level().dimension().identifier()
    )
    if (error != null) {
        sendModifySelectionTargetError(player, error)
        return null
    }
    return state.points
}

private fun modifyPolygonScope(
    player: ServerPlayer,
    targetRegion: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
): Boolean {
    return when (selectedPositions.size) {
        1 -> modifyScopePolygonMonoPoint(player, targetRegion, existingScope, selectedPositions)
        2 -> modifyScopePolygonMove(player, targetRegion, existingScope, selectedPositions)
        else -> modifyScopePolygonInsertPoint(player, targetRegion, existingScope, selectedPositions)
    }
}

private fun modifyCircleScope(
    player: ServerPlayer,
    targetRegion: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
): Boolean {
    return if (selectedPositions.size == 1) {
        modifyScopeCircleRadius(player, targetRegion, existingScope, selectedPositions)
    } else {
        modifyScopeCircleCenter(player, targetRegion, existingScope, selectedPositions)
    }
}
