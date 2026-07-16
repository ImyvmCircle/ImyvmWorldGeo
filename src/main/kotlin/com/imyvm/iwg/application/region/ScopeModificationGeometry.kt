package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.*
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.hypot

internal fun updateRectangleBounds(point: BlockPos, shapeParams: List<Int>): IntArray {
    require(shapeParams.size == 4) { "rectangle requires west/north/east/south" }
    var west = shapeParams[0]
    var north = shapeParams[1]
    var east = shapeParams[2]
    var south = shapeParams[3]

    if (abs(point.x.toLong() - west) < abs(point.x.toLong() - east)) west = point.x else east = point.x
    if (abs(point.z.toLong() - north) < abs(point.z.toLong() - south)) north = point.z else south = point.z
    return intArrayOf(west, north, east, south)
}

internal fun modifyRectangle(geometry: RectangleGeometry, point: BlockPos): Result<GeoShape, CreationError> {
    var west = geometry.west
    var north = geometry.north
    var east = geometry.east
    var south = geometry.south

    if (abs(point.x.toLong() - west) < abs(point.x.toLong() - east)) west = point.x else east = point.x
    if (abs(point.z.toLong() - north) < abs(point.z.toLong() - south)) north = point.z else south = point.z

    val positions = listOf(
        BlockPos(minOf(west, east), 0, minOf(north, south)),
        BlockPos(maxOf(west, east), 0, maxOf(north, south))
    )
    return constructShape(positions, GeoShapeType.RECTANGLE)
}

internal fun modifyCircleRadius(geometry: CircleGeometry, point: BlockPos): Result<GeoShape, CreationError> {
    val positions = listOf(
        BlockPos(geometry.centerX, 0, geometry.centerZ),
        point
    )
    return constructShape(positions, GeoShapeType.CIRCLE)
}

internal fun modifyCircleCenter(geometry: CircleGeometry, newCenter: BlockPos): Result<GeoShape, CreationError> {
    val edgeX = newCenter.x.toLong() + geometry.radius
    if (edgeX !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        return Result.Err(CreationError.CoordinateRangeExceeded)
    }
    val positions = listOf(
        newCenter,
        BlockPos(edgeX.toInt(), 0, newCenter.z)
    )
    return constructShape(positions, GeoShapeType.CIRCLE)
}

internal sealed interface PolygonModificationResult {
    data class Shape(val result: Result<GeoShape, CreationError>) : PolygonModificationResult
    data object MinimumPoints : PolygonModificationResult
    data object PointNotFound : PolygonModificationResult
    data object PointsNotAdjacent : PolygonModificationResult
}

internal fun modifyPolygonDelete(geometry: PolygonGeometry, point: BlockPos): PolygonModificationResult {
    if (geometry.vertexCount <= 3) return PolygonModificationResult.MinimumPoints
    val vertices = geometryToBlockPosList(geometry)
    val filtered = vertices.filter { !(it.x == point.x && it.z == point.z) }
    if (filtered.size == vertices.size) return PolygonModificationResult.PointNotFound
    return PolygonModificationResult.Shape(constructShape(filtered, GeoShapeType.POLYGON))
}

internal fun modifyPolygonMove(
    geometry: PolygonGeometry,
    oldPoint: BlockPos,
    newPoint: BlockPos
): PolygonModificationResult {
    if (oldPoint.x == newPoint.x && oldPoint.z == newPoint.z) {
        return PolygonModificationResult.Shape(Result.Err(CreationError.DuplicatedPoints))
    }
    val vertices = geometryToBlockPosList(geometry)
    if (vertices.none { it.x == oldPoint.x && it.z == oldPoint.z }) {
        return PolygonModificationResult.PointNotFound
    }
    val moved = vertices.map { if (it.x == oldPoint.x && it.z == oldPoint.z) newPoint else it }
    return PolygonModificationResult.Shape(constructShape(moved, GeoShapeType.POLYGON))
}

internal fun modifyPolygonInsert(
    geometry: PolygonGeometry,
    adj1: BlockPos,
    adj2: BlockPos,
    newPoint: BlockPos
): PolygonModificationResult {
    val vertices = geometryToBlockPosList(geometry)
    val n = vertices.size
    val indexA = vertices.indexOfFirst { it.x == adj1.x && it.z == adj1.z }
    val indexB = vertices.indexOfFirst { it.x == adj2.x && it.z == adj2.z }
    if (indexA == -1 || indexB == -1) return PolygonModificationResult.PointNotFound
    if ((indexA + 1) % n != indexB && (indexB + 1) % n != indexA) {
        return PolygonModificationResult.PointsNotAdjacent
    }
    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    val newList = vertices.toMutableList().apply { add(insertIndex, newPoint) }
    return PolygonModificationResult.Shape(constructShape(newList, GeoShapeType.POLYGON))
}

internal fun geometryToBlockPosList(geometry: PolygonGeometry): List<BlockPos> =
    List(geometry.vertexCount) { BlockPos(geometry.x(it), 0, geometry.z(it)) }
