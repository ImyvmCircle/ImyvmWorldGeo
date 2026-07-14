package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.application.region.updateRectangleBounds
import com.imyvm.iwg.domain.component.MAX_POLYGON_VERTICES
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.util.geo.checkCircleSize
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.checkRectangleSize
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.geo.isConvex
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.hypot

fun evaluateRectangleShape(pos1: BlockPos, pos2: BlockPos): List<Int>? {
    if (pos1.x == pos2.x || pos1.z == pos2.z) return null
    val west = minOf(pos1.x, pos2.x)
    val east = maxOf(pos1.x, pos2.x)
    val north = minOf(pos1.z, pos2.z)
    val south = maxOf(pos1.z, pos2.z)
    val width = east.toLong() - west
    val length = south.toLong() - north
    if (checkRectangleSize(width, length) != null) return null
    return listOf(west, north, east, south)
}

fun evaluateCircleShape(center: BlockPos, edge: BlockPos): List<Int>? {
    if (center.x == edge.x && center.z == edge.z) return null
    val radius = hypot(edge.x.toDouble() - center.x, edge.z.toDouble() - center.z)
    return evaluatedCircle(center.x, center.z, radius)
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
    return evaluatedCircle(centerX, centerZ, radius)
}

fun evaluateModifyCircleCenter(oldCenter: BlockPos, newCenter: BlockPos, existingParams: List<Int>): List<Int>? {
    if (oldCenter.x != existingParams[0] || oldCenter.z != existingParams[1]) return null
    val radius = existingParams[2]
    return evaluatedCircle(newCenter.x, newCenter.z, radius.toDouble())
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

private fun evaluatedCircle(centerX: Int, centerZ: Int, radius: Double): List<Int>? {
    if (!checkCircleSize(radius) || radius > Int.MAX_VALUE) return null
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
