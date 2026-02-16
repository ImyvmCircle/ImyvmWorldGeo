package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.sqrt

object ScopeAreaChangeEstimator {

    fun estimateScopeModificationAreaChange(
        existingScope: GeoScope,
        selectedPositions: List<BlockPos>
    ): AreaEstimationResult {
        val geoShape = existingScope.geoShape
            ?: return AreaEstimationResult.Error(CreationError.InsufficientPoints)

        val currentArea = geoShape.calculateArea()
        val shapeType = geoShape.geoShapeType

        val newPositions = when (shapeType) {
            GeoShapeType.RECTANGLE -> getRectangleModificationPositions(geoShape.shapeParameter, selectedPositions)
            GeoShapeType.CIRCLE -> getCircleModificationPositions(geoShape.shapeParameter, selectedPositions)
            GeoShapeType.POLYGON -> getPolygonModificationPositions(geoShape.shapeParameter, selectedPositions)
            else -> return AreaEstimationResult.Error(CreationError.InsufficientPoints)
        } ?: return AreaEstimationResult.Error(CreationError.InsufficientPoints)

        return when (val result = AreaEstimator.estimateShapeArea(newPositions, shapeType)) {
            is AreaEstimationResult.Success -> AreaEstimationResult.Success(result.area - currentArea)
            is AreaEstimationResult.Error -> result
        }
    }

    private fun getRectangleModificationPositions(
        shapeParams: List<Int>,
        selectedPositions: List<BlockPos>
    ): List<BlockPos>? {
        if (shapeParams.size < 4 || selectedPositions.isEmpty()) return null

        val point = selectedPositions[0]
        var west = shapeParams[0]
        var north = shapeParams[1]
        var east = shapeParams[2]
        var south = shapeParams[3]

        if (abs(point.x - west) < abs(point.x - east)) west = point.x else east = point.x
        if (abs(point.z - north) < abs(point.z - south)) north = point.z else south = point.z

        return listOf(BlockPos(west, 0, north), BlockPos(east, 0, south))
    }

    private fun getCircleModificationPositions(
        shapeParams: List<Int>,
        selectedPositions: List<BlockPos>
    ): List<BlockPos>? {
        if (shapeParams.size < 3 || selectedPositions.isEmpty()) return null

        val centerX = shapeParams[0]
        val centerZ = shapeParams[1]
        val radius = shapeParams[2]

        return if (selectedPositions.size == 1) {
            val pos = selectedPositions[0]
            val dx = pos.x - centerX
            val dz = pos.z - centerZ
            val newRadius = sqrt((dx * dx + dz * dz).toDouble()).toInt()
            if (newRadius <= 0) return null
            listOf(BlockPos(centerX, 0, centerZ), BlockPos(centerX + newRadius, 0, centerZ))
        } else {
            val newCenter = selectedPositions[1]
            listOf(BlockPos(newCenter.x, 0, newCenter.z), BlockPos(newCenter.x + radius, 0, newCenter.z))
        }
    }

    private fun getPolygonModificationPositions(
        shapeParams: List<Int>,
        selectedPositions: List<BlockPos>
    ): List<BlockPos>? {
        if (shapeParams.size < 6 || shapeParams.size % 2 != 0 || selectedPositions.isEmpty()) return null

        val blockPosList = shapeParams.chunked(2).map { BlockPos(it[0], 0, it[1]) }

        return when (selectedPositions.size) {
            1 -> handlePolygonMonoPoint(blockPosList, selectedPositions[0])
            2 -> handlePolygonMove(blockPosList, selectedPositions[0], selectedPositions[1])
            else -> handlePolygonInsert(blockPosList, selectedPositions)
        }
    }

    private fun handlePolygonMonoPoint(blockPosList: List<BlockPos>, point: BlockPos): List<BlockPos>? {
        return if (blockPosList.any { it.x == point.x && it.z == point.z }) {
            if (blockPosList.size <= 3) null
            else blockPosList.filterNot { it.x == point.x && it.z == point.z }
        } else {
            val (pointA, pointB) = findNearestAdjacentPoints(blockPosList, point)
            val indexA = blockPosList.indexOfFirst { it.x == pointA.x && it.z == pointA.z }
            val indexB = blockPosList.indexOfFirst { it.x == pointB.x && it.z == pointB.z }
            val insertIndex = if ((indexA + 1) % blockPosList.size == indexB) indexB else indexA
            blockPosList.toMutableList().apply { add(insertIndex, point) }
        }
    }

    private fun handlePolygonMove(blockPosList: List<BlockPos>, oldPoint: BlockPos, newPoint: BlockPos): List<BlockPos>? {
        if (blockPosList.none { it.x == oldPoint.x && it.z == oldPoint.z }) return null
        return blockPosList.map { if (it.x == oldPoint.x && it.z == oldPoint.z) newPoint else it }
    }

    private fun handlePolygonInsert(blockPosList: List<BlockPos>, selectedPositions: List<BlockPos>): List<BlockPos>? {
        if (selectedPositions.size < 3) return null
        val (pointA, pointB, newPoint) = selectedPositions

        val indexA = blockPosList.indexOfFirst { it.x == pointA.x && it.z == pointA.z }
        val indexB = blockPosList.indexOfFirst { it.x == pointB.x && it.z == pointB.z }

        if (indexA == -1 || indexB == -1) return null

        val n = blockPosList.size
        val areAdjacent = (indexA + 1) % n == indexB || (indexB + 1) % n == indexA
        if (!areAdjacent) return null

        val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
        return blockPosList.toMutableList().apply { add(insertIndex, newPoint) }
    }
}
