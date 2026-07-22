package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.application.interaction.clearPlayerSelection
import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.interaction.saveRegionData
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun recreateScope(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    newPositions: List<BlockPos>,
    shapeType: GeoShapeType
): Boolean {
    require(region.containsScope(existingScope)) { "scope does not belong to region" }

    val newShape = RegionFactory.recreateScopeShape(
        region = region,
        existingScope = existingScope,
        selectedPositions = newPositions,
        shapeType = shapeType
    )

    return when (newShape) {
        is Result.Ok -> {
            if (replaceScopeGeometryAndSave(region, existingScope, newShape.value) { saveRegionData(player) }) {
                clearSelectionDisplay(player)
                clearPlayerSelection(player.uuid)
                true
            } else false
        }
        is Result.Err -> {
            val errorMsg = errorMessage(newShape.error, shapeType)
            errorMsg.forEach { player.sendSystemMessage(it) }
            false
        }
    }
}

fun onReplacingScopeShape(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    newShape: GeoShape
): Int = when (val result = replaceScopeShape(region, scope, newShape) { saveRegionData(player) }) {
    ScopeShapeReplacementResult.Success -> {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.shape_replace.success",
            scope.scopeName,
            region.name
        )))
        1
    }
    is ScopeShapeReplacementResult.Rejected -> {
        errorMessage(result.error, newShape.geoShapeType).forEach(player::sendSystemMessage)
        0
    }
    ScopeShapeReplacementResult.PersistenceFailed -> 0
}

internal sealed interface ScopeShapeReplacementResult {
    data object Success : ScopeShapeReplacementResult
    data class Rejected(val error: CreationError) : ScopeShapeReplacementResult
    data object PersistenceFailed : ScopeShapeReplacementResult
}

internal fun replaceScopeShape(
    region: Region,
    scope: GeoScope,
    newShape: GeoShape,
    currentRegions: List<Region> = RegionDatabase.getRegionList(),
    save: () -> Boolean
): ScopeShapeReplacementResult {
    RegionDatabase.requireCanonicalScope(region, scope, currentRegions)
    val oldShape = requireNotNull(scope.geoShape) { "scope must have a shape" }
    require(oldShape.geoShapeType != GeoShapeType.UNKNOWN) { "scope must have a supported shape" }
    require(oldShape.geoShapeType == newShape.geoShapeType) { "scope shape type cannot be changed" }

    val error = RegionFactory.validateGeoShapePlacement(newShape, scope.worldId, scope, currentRegions)
    if (error != null) return ScopeShapeReplacementResult.Rejected(error)
    return if (replaceScopeGeometryAndSave(region, scope, newShape, save)) {
        ScopeShapeReplacementResult.Success
    } else {
        ScopeShapeReplacementResult.PersistenceFailed
    }
}

internal fun replaceScopeGeometryAndSave(
    region: Region,
    scope: GeoScope,
    newShape: GeoShape,
    save: () -> Boolean
): Boolean {
    val oldShape = scope.geoShape
    region.replaceScopeGeometryFromOwner(scope, newShape)
    return try {
        if (save()) true else {
            region.replaceScopeGeometryFromOwner(scope, oldShape)
            false
        }
    } catch (error: Exception) {
        region.replaceScopeGeometryFromOwner(scope, oldShape)
        throw error
    }
}
