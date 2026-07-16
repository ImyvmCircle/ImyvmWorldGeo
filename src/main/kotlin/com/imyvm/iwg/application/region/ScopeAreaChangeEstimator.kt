package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import net.minecraft.core.BlockPos

object ScopeAreaChangeEstimator {

    fun estimateScopeModificationAreaChange(
        existingScope: GeoScope,
        selectedPositions: List<BlockPos>
    ): AreaEstimationResult {
        val geoShape = existingScope.geoShape
            ?: return AreaEstimationResult.Error(CreationError.InsufficientPoints)

        val currentArea = geoShape.calculateArea()
        val geometry = geoShape.typedGeometry

        val shapeResult = when (geometry) {
            is RectangleGeometry -> estimateRectangleModification(geometry, selectedPositions)
            is CircleGeometry -> estimateCircleModification(geometry, selectedPositions)
            is PolygonGeometry -> estimatePolygonModification(geometry, selectedPositions)
            UnknownGeometry -> return AreaEstimationResult.Error(CreationError.InsufficientPoints)
        } ?: return AreaEstimationResult.Error(CreationError.InsufficientPoints)

        return when (shapeResult) {
            is Result.Ok -> AreaEstimationResult.Success(shapeResult.value.calculateArea() - currentArea)
            is Result.Err -> AreaEstimationResult.Error(shapeResult.error)
        }
    }

    private fun estimateRectangleModification(
        geometry: RectangleGeometry,
        selectedPositions: List<BlockPos>
    ): Result<GeoShape, CreationError>? {
        if (selectedPositions.size != 1) return null
        return modifyRectangle(geometry, selectedPositions[0])
    }

    private fun estimateCircleModification(
        geometry: CircleGeometry,
        selectedPositions: List<BlockPos>
    ): Result<GeoShape, CreationError>? {
        return when (selectedPositions.size) {
            1 -> modifyCircleRadius(geometry, selectedPositions[0])
            2 -> {
                val oldCenter = selectedPositions[0]
                if (oldCenter.x != geometry.centerX || oldCenter.z != geometry.centerZ) return null
                modifyCircleCenter(geometry, selectedPositions[1])
            }
            else -> null
        }
    }

    private fun estimatePolygonModification(
        geometry: PolygonGeometry,
        selectedPositions: List<BlockPos>
    ): Result<GeoShape, CreationError>? {
        if (selectedPositions.isEmpty()) return null
        val vertices = geometryToBlockPosList(geometry)

        val modification = when (selectedPositions.size) {
            1 -> {
                val point = selectedPositions[0]
                if (vertices.any { it.x == point.x && it.z == point.z }) {
                    modifyPolygonDelete(geometry, point)
                } else {
                    val (pointA, pointB) = findNearestAdjacentPoints(vertices, point)
                    modifyPolygonInsert(geometry, pointA, pointB, point)
                }
            }
            2 -> modifyPolygonMove(geometry, selectedPositions[0], selectedPositions[1])
            3 -> modifyPolygonInsert(geometry, selectedPositions[0], selectedPositions[1], selectedPositions[2])
            else -> return null
        }
        return (modification as? PolygonModificationResult.Shape)?.result
    }
}
