package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.core.BlockPos

object AreaEstimator {

    fun estimateShapeArea(
        positions: List<BlockPos>,
        shapeType: GeoShapeType
    ): AreaEstimationResult {
        val shapeResult = constructShape(positions, shapeType)
        return when (shapeResult) {
            is Result.Ok -> AreaEstimationResult.Success(shapeResult.value.calculateArea())
            is Result.Err -> AreaEstimationResult.Error(shapeResult.error)
        }
    }
}
