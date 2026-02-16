package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.geo.*
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.sqrt

object AreaEstimator {

    fun estimateShapeArea(
        positions: List<BlockPos>,
        shapeType: GeoShapeType
    ): AreaEstimationResult {
        val requiredPoints = when (shapeType) {
            GeoShapeType.CIRCLE, GeoShapeType.RECTANGLE -> 2
            GeoShapeType.POLYGON -> 3
            else -> return AreaEstimationResult.Error(CreationError.InsufficientPoints)
        }

        if (positions.size < requiredPoints) {
            return AreaEstimationResult.Error(CreationError.InsufficientPoints)
        }

        return when (shapeType) {
            GeoShapeType.RECTANGLE -> estimateRectangleArea(positions)
            GeoShapeType.CIRCLE -> estimateCircleArea(positions)
            GeoShapeType.POLYGON -> estimatePolygonArea(positions)
            else -> AreaEstimationResult.Error(CreationError.InsufficientPoints)
        }
    }

    private fun estimateRectangleArea(positions: List<BlockPos>): AreaEstimationResult {
        val pos1 = positions[0]
        val pos2 = positions[1]

        if (pos1 == pos2) return AreaEstimationResult.Error(CreationError.DuplicatedPoints)
        if (pos1.x == pos2.x || pos1.z == pos2.z) return AreaEstimationResult.Error(CreationError.CoincidentPoints)

        val width = abs(pos1.x - pos2.x)
        val length = abs(pos1.z - pos2.z)
        
        val error = checkRectangleSize(width, length)
        if (error != null) return AreaEstimationResult.Error(error)

        val area = (width * length).toDouble()
        return AreaEstimationResult.Success(area)
    }

    private fun estimateCircleArea(positions: List<BlockPos>): AreaEstimationResult {
        val center = positions[0]
        val circumference = positions[1]

        if (center == circumference) return AreaEstimationResult.Error(CreationError.DuplicatedPoints)

        val dx = circumference.x - center.x
        val dz = circumference.z - center.z
        val radius = sqrt((dx * dx + dz * dz).toDouble())
        
        if (!checkCircleSize(radius)) return AreaEstimationResult.Error(CreationError.UnderSizeLimit)

        val area = Math.PI * radius * radius
        return AreaEstimationResult.Success(area)
    }

    private fun estimatePolygonArea(positions: List<BlockPos>): AreaEstimationResult {
        val distinct = positions.distinct()
        if (distinct.size != positions.size) return AreaEstimationResult.Error(CreationError.DuplicatedPoints)
        
        if (!isConvex(positions)) return AreaEstimationResult.Error(CreationError.NotConvex)
        
        val error = checkPolygonSize(positions)
        if (error != null) return AreaEstimationResult.Error(error)

        val area = calculatePolygonArea(positions)
        return AreaEstimationResult.Success(area)
    }
}
