package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.util.geo.checkCircleSize
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.checkRectangleSize
import com.imyvm.iwg.util.geo.isConvex
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.sqrt

fun evaluateRectangleShape(pos1: BlockPos, pos2: BlockPos): List<Int>? {
    if (pos1.x == pos2.x || pos1.z == pos2.z) return null
    val west = minOf(pos1.x, pos2.x)
    val east = maxOf(pos1.x, pos2.x)
    val north = minOf(pos1.z, pos2.z)
    val south = maxOf(pos1.z, pos2.z)
    val width = east - west
    val length = south - north
    if (checkRectangleSize(width, length) != null) return null
    return listOf(west, north, east, south)
}

fun evaluateCircleShape(center: BlockPos, edge: BlockPos): List<Int>? {
    if (center.x == edge.x && center.z == edge.z) return null
    val dx = (edge.x - center.x).toDouble()
    val dz = (edge.z - center.z).toDouble()
    val radius = sqrt(dx * dx + dz * dz)
    if (!checkCircleSize(radius)) return null
    return listOf(center.x, center.z, radius.toInt())
}

fun evaluateModifyRectangle(newPoint: BlockPos, existingParams: List<Int>): List<Int>? {
    var west = existingParams[0]
    var north = existingParams[1]
    var east = existingParams[2]
    var south = existingParams[3]

    if (abs(newPoint.x - west) < abs(newPoint.x - east)) west = newPoint.x else east = newPoint.x
    if (abs(newPoint.z - north) < abs(newPoint.z - south)) north = newPoint.z else south = newPoint.z

    val width = abs(east - west)
    val length = abs(south - north)
    if (checkRectangleSize(width, length) != null) return null
    return listOf(minOf(west, east), minOf(north, south), maxOf(west, east), maxOf(north, south))
}

fun evaluateModifyCircleRadius(newPoint: BlockPos, existingParams: List<Int>): List<Int>? {
    val centerX = existingParams[0]
    val centerZ = existingParams[1]
    val dx = (newPoint.x - centerX).toDouble()
    val dz = (newPoint.z - centerZ).toDouble()
    val radius = sqrt(dx * dx + dz * dz)
    if (!checkCircleSize(radius)) return null
    return listOf(centerX, centerZ, radius.toInt())
}

fun evaluateModifyCircleCenter(oldCenter: BlockPos, newCenter: BlockPos, existingParams: List<Int>): List<Int>? {
    val radius = existingParams[2]
    if (!checkCircleSize(radius.toDouble())) return null
    return listOf(newCenter.x, newCenter.z, radius)
}

fun evaluateModifyPolygonInsert(newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    if (existingPoints.size < 3) return null
    var bestIndex = 0
    var bestDist = Double.MAX_VALUE
    val n = existingPoints.size
    for (i in 0 until n) {
        val a = existingPoints[i]
        val b = existingPoints[(i + 1) % n]
        val mx = (a.x + b.x) / 2.0
        val mz = (a.z + b.z) / 2.0
        val dx = newPoint.x - mx
        val dz = newPoint.z - mz
        val dist = dx * dx + dz * dz
        if (dist < bestDist) {
            bestDist = dist
            bestIndex = (i + 1) % n
        }
    }
    val newPolygon = existingPoints.toMutableList()
    newPolygon.add(bestIndex, newPoint)
    if (!isConvex(newPolygon)) return null
    if (checkPolygonSize(newPolygon) != null) return null
    return newPolygon
}

fun evaluateModifyPolygonReplace(existingVertex: BlockPos, newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    val newPolygon = existingPoints.map {
        if (it.x == existingVertex.x && it.z == existingVertex.z) newPoint else it
    }
    if (!isConvex(newPolygon)) return null
    if (checkPolygonSize(newPolygon) != null) return null
    return newPolygon
}

fun evaluateModifyPolygonExplicitInsert(adj1: BlockPos, adj2: BlockPos, newPoint: BlockPos, existingPoints: List<BlockPos>): List<BlockPos>? {
    val n = existingPoints.size
    val indexA = existingPoints.indexOfFirst { it.x == adj1.x && it.z == adj1.z }
    val indexB = existingPoints.indexOfFirst { it.x == adj2.x && it.z == adj2.z }
    if (indexA == -1 || indexB == -1) return null
    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    val newPolygon = existingPoints.toMutableList()
    newPolygon.add(insertIndex, newPoint)
    if (!isConvex(newPolygon)) return null
    if (checkPolygonSize(newPolygon) != null) return null
    return newPolygon
}