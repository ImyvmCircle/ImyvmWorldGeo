package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.application.region.updateRectangleBounds
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.MAX_POLYGON_VERTICES
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.util.geo.GeometrySizeLimits
import com.imyvm.iwg.util.geo.checkCircleSize
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.checkRectangleSize
import com.imyvm.iwg.util.geo.regionGeometrySizeLimits
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.geo.isConvex
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.hypot

fun evaluateRectangleShape(pos1: BlockPos, pos2: BlockPos): List<Int>? =
    evaluateRectangleShape(pos1, pos2, regionGeometrySizeLimits)

internal fun evaluateRectangleShape(pos1: BlockPos, pos2: BlockPos, limits: GeometrySizeLimits): List<Int>? {
    if (pos1.x == pos2.x || pos1.z == pos2.z) return null
    val west = minOf(pos1.x, pos2.x)
    val east = maxOf(pos1.x, pos2.x)
    val north = minOf(pos1.z, pos2.z)
    val south = maxOf(pos1.z, pos2.z)
    val width = east.toLong() - west
    val length = south.toLong() - north
    if (checkRectangleSize(width, length, limits) != null) return null
    return listOf(west, north, east, south)
}

fun evaluateCircleShape(center: BlockPos, edge: BlockPos): List<Int>? =
    evaluateCircleShape(center, edge, regionGeometrySizeLimits)

internal fun evaluateCircleShape(center: BlockPos, edge: BlockPos, limits: GeometrySizeLimits): List<Int>? {
    if (center.x == edge.x && center.z == edge.z) return null
    val radius = hypot(edge.x.toDouble() - center.x, edge.z.toDouble() - center.z)
    return evaluatedCircle(center.x, center.z, radius, limits)
}

fun evaluateModifyRectangle(newPoint: BlockPos, existingParams: List<Int>): List<Int>? {
    val (west, north, east, south) = updateRectangleBounds(newPoint, existingParams)
    val width = abs(east.toLong() - west)
    val length = abs(south.toLong() - north)
    if (checkRectangleSize(width, length) != null) return null
    return listOf(minOf(west, east), minOf(north, south), maxOf(west, east), maxOf(north, south))
}

fun evaluateModifyCircleRadius(newPoint: BlockPos, existingParams: List<Int>): List<Int>? {
    val centerX = existingParams[0]
    val centerZ = existingParams[1]
    val radius = hypot(newPoint.x.toDouble() - centerX, newPoint.z.toDouble() - centerZ)
    return evaluatedCircle(centerX, centerZ, radius, regionGeometrySizeLimits)
}

fun evaluateModifyCircleCenter(oldCenter: BlockPos, newCenter: BlockPos, existingParams: List<Int>): List<Int>? {
    if (oldCenter.x != existingParams[0] || oldCenter.z != existingParams[1]) return null
    val radius = existingParams[2]
    return evaluatedCircle(newCenter.x, newCenter.z, radius.toDouble(), regionGeometrySizeLimits)
}

internal fun evaluateModifiedShape(geometry: com.imyvm.iwg.domain.component.ShapeGeometry, selectedPoints: List<BlockPos>): GeoShape? = when (geometry) {
    is com.imyvm.iwg.domain.component.RectangleGeometry -> if (selectedPoints.size == 1) {
        evaluateModifyRectangle(selectedPoints[0], listOf(geometry.west, geometry.north, geometry.east, geometry.south))
            ?.let { GeoShape.rectangle(GeoPoint(it[0], it[1]), GeoPoint(it[2], it[3])) }
    } else null
    is com.imyvm.iwg.domain.component.CircleGeometry -> when (selectedPoints.size) {
        1 -> evaluateModifyCircleRadius(selectedPoints[0], listOf(geometry.centerX, geometry.centerZ, geometry.radius))
            ?.let { GeoShape.circle(GeoPoint(it[0], it[1]), it[2]) }
        2 -> evaluateModifyCircleCenter(selectedPoints[0], selectedPoints[1], listOf(geometry.centerX, geometry.centerZ, geometry.radius))
            ?.let { GeoShape.circle(GeoPoint(it[0], it[1]), it[2]) }
        else -> null
    }
    is com.imyvm.iwg.domain.component.PolygonGeometry -> when (selectedPoints.size) {
        1 -> {
            val existing = List(geometry.vertexCount) { index -> BlockPos(geometry.x(index), 0, geometry.z(index)) }
            val point = selectedPoints[0]
            val polygon = if (existing.any { it.x == point.x && it.z == point.z }) {
                existing.filterNot { it.x == point.x && it.z == point.z }
            } else {
                evaluateModifyPolygonInsert(point, existing)
            }
            polygon?.takeIf { it.size >= 3 }?.let { GeoShape.polygon(it.map { vertex -> GeoPoint(vertex.x, vertex.z) }) }
        }
        2 -> {
            val existing = List(geometry.vertexCount) { index -> BlockPos(geometry.x(index), 0, geometry.z(index)) }
            evaluateModifyPolygonReplace(selectedPoints[0], selectedPoints[1], existing)
                ?.let { GeoShape.polygon(it.map { vertex -> GeoPoint(vertex.x, vertex.z) }) }
        }
        3 -> {
            val existing = List(geometry.vertexCount) { index -> BlockPos(geometry.x(index), 0, geometry.z(index)) }
            evaluateModifyPolygonExplicitInsert(selectedPoints[0], selectedPoints[1], selectedPoints[2], existing)
                ?.let { GeoShape.polygon(it.map { vertex -> GeoPoint(vertex.x, vertex.z) }) }
        }
        else -> null
    }
    com.imyvm.iwg.domain.component.UnknownGeometry -> null
}

fun evaluateModifyPolygonInsert(newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    if (existingPoints.size < 3) return null
    if (existingPoints.size >= MAX_POLYGON_VERTICES) return null
    val (adjacentA, adjacentB) = findNearestAdjacentPoints(existingPoints, newPoint)
    return evaluateModifyPolygonExplicitInsert(adjacentA, adjacentB, newPoint, existingPoints)
}

fun evaluateModifyPolygonReplace(existingVertex: BlockPos, newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    if (!isPolygonVertexCountSupported(existingPoints.size)) return null
    val sourceIndex = existingPoints.indexOfFirst { it.sameColumn(existingVertex) }
    if (sourceIndex == -1) return null
    val newPolygon = existingPoints.toMutableList().apply { this[sourceIndex] = newPoint }
    return validatedPolygon(newPolygon)
}

fun evaluateModifyPolygonExplicitInsert(adj1: BlockPos, adj2: BlockPos, newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    if (existingPoints.size < 3 || existingPoints.size >= MAX_POLYGON_VERTICES) return null
    val n = existingPoints.size
    val indexA = existingPoints.indexOfFirst { it.sameColumn(adj1) }
    val indexB = existingPoints.indexOfFirst { it.sameColumn(adj2) }
    if (indexA == -1 || indexB == -1) return null
    if ((indexA + 1) % n != indexB && (indexB + 1) % n != indexA) return null
    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    val newPolygon = existingPoints.toMutableList()
    newPolygon.add(insertIndex, newPoint)
    return validatedPolygon(newPolygon)
}

private fun evaluatedCircle(centerX: Int, centerZ: Int, radius: Double, limits: GeometrySizeLimits): List<Int>? {
    if (!checkCircleSize(radius, limits) || radius > Int.MAX_VALUE) return null
    val intRadius = radius.toInt()
    val min = Int.MIN_VALUE.toLong()
    val max = Int.MAX_VALUE.toLong()
    if (centerX.toLong() - intRadius !in min..max || centerX.toLong() + intRadius !in min..max ||
        centerZ.toLong() - intRadius !in min..max || centerZ.toLong() + intRadius !in min..max
    ) return null
    return listOf(centerX, centerZ, intRadius)
}

private fun validatedPolygon(points: List<BlockPos>): List<BlockPos>? {
    if (points.distinctBy { it.x to it.z }.size != points.size) return null
    if (!isConvex(points) || checkPolygonSize(points) != null) return null
    return points
}

private fun BlockPos.sameColumn(other: BlockPos): Boolean = x == other.x && z == other.z
