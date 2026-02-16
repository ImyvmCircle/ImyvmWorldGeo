package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.AreaEstimator
import com.imyvm.iwg.application.region.ScopeAreaChangeEstimator
import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onEstimateRegionArea(
    player: ServerPlayerEntity,
    shapeTypeName: String,
    customPositions: List<BlockPos>? = null
): AreaEstimationResult {
    val shapeType = GeoShapeType.entries.find { it.name == shapeTypeName } ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        return AreaEstimationResult.Error(com.imyvm.iwg.domain.CreationError.InsufficientPoints)
    }
    val positions = customPositions ?: ImyvmWorldGeo.pointSelectingPlayers[player.uuid] ?: emptyList()
    return AreaEstimator.estimateShapeArea(positions, shapeType)
}

fun onEstimateScopeAreaChange(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String,
    customPositions: List<BlockPos>? = null
): AreaEstimationResult {
    return try {
        val existingScope = region.getScopeByName(scopeName)
        val positions = customPositions ?: ImyvmWorldGeo.pointSelectingPlayers[player.uuid] ?: emptyList()
        ScopeAreaChangeEstimator.estimateScopeModificationAreaChange(existingScope, positions)
    } catch (e: IllegalArgumentException) {
        AreaEstimationResult.Error(com.imyvm.iwg.domain.CreationError.InsufficientPoints)
    }
}
