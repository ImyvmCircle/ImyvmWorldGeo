package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.application.region.updateRectangleBounds
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.MAX_POLYGON_VERTICES
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.ShapeGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.util.geo.GeometrySizeLimits
import com.imyvm.iwg.util.geo.checkCircleSize
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.checkRectangleSize
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.geo.isConvex
import com.imyvm.iwg.util.geo.regionGeometrySizeLimits
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.hypot

internal sealed class ModifiedShapeEvaluation {
    data object Incomplete : ModifiedShapeEvaluation()
    data class Invalid(val error: CreationError? = null) : ModifiedShapeEvaluation()
    data class Valid(val shape: GeoShape) : ModifiedShapeEvaluation()
}

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

internal fun evaluateModifyRectangle(
    newPoint: BlockPos,
    existingParams: List<Int>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<Int>? {
    val (west, north, east, south) = updateRectangleBounds(newPoint, existingParams)
    val width = abs(east.toLong() - west)
    val length = abs(south.toLong() - north)
    if (checkRectangleSize(width, length, limits) != null) return null
    return listOf(minOf(west, east), minOf(north, south), maxOf(west, east), maxOf(north, south))
}

internal fun evaluateModifyCircleRadius(
    newPoint: BlockPos,
    existingParams: List<Int>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<Int>? {
    val centerX = existingParams[0]
    val centerZ = existingParams[1]
    val radius = hypot(newPoint.x.toDouble() - centerX, newPoint.z.toDouble() - centerZ)
    return evaluatedCircle(centerX, centerZ, radius, limits)
}

internal fun evaluateModifyCircleCenter(
    oldCenter: BlockPos,
    newCenter: BlockPos,
    existingParams: List<Int>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<Int>? {
    if (oldCenter.x != existingParams[0] || oldCenter.z != existingParams[1]) return null
    val radius = existingParams[2]
    return evaluatedCircle(newCenter.x, newCenter.z, radius.toDouble(), limits)
}

internal fun evaluateModifiedShape(
    geometry: ShapeGeometry,
    selectedPoints: List<BlockPos>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): GeoShape? = (evaluateModifiedShapePreview(geometry, selectedPoints, limits) as? ModifiedShapeEvaluation.Valid)?.shape

internal fun evaluateModifiedShapePreview(
    geometry: ShapeGeometry,
    selectedPoints: List<BlockPos>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): ModifiedShapeEvaluation = when (geometry) {
    is RectangleGeometry -> evaluateModifiedRectangleShape(geometry, selectedPoints, limits)
    is CircleGeometry -> evaluateModifiedCircleShape(geometry, selectedPoints, limits)
    is PolygonGeometry -> evaluateModifiedPolygonShape(geometry, selectedPoints, limits)
    UnknownGeometry -> ModifiedShapeEvaluation.Invalid(CreationError.InsufficientPoints)
}

internal fun evaluateModifyPolygonInsert(
    newPoint: BlockPos,
    existingPoints: List<BlockPos>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<BlockPos>? {
    if (existingPoints.size < 3) return null
    if (existingPoints.size >= MAX_POLYGON_VERTICES) return null
    val (adjacentA, adjacentB) = findNearestAdjacentPoints(existingPoints, newPoint)
    return evaluateModifyPolygonExplicitInsert(adjacentA, adjacentB, newPoint, existingPoints, limits)
}

internal fun evaluateModifyPolygonReplace(
    existingVertex: BlockPos,
    newPoint: BlockPos,
    existingPoints: List<BlockPos>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<BlockPos>? {
    if (!isPolygonVertexCountSupported(existingPoints.size)) return null
    val sourceIndex = existingPoints.indexOfFirst { it.sameColumn(existingVertex) }
    if (sourceIndex == -1) return null
    val newPolygon = existingPoints.toMutableList().apply { this[sourceIndex] = newPoint }
    return validatedPolygon(newPolygon, limits)
}

internal fun evaluateModifyPolygonExplicitInsert(
    adj1: BlockPos,
    adj2: BlockPos,
    newPoint: BlockPos,
    existingPoints: List<BlockPos>,
    limits: GeometrySizeLimits = regionGeometrySizeLimits
): List<BlockPos>? {
    if (existingPoints.size < 3 || existingPoints.size >= MAX_POLYGON_VERTICES) return null
    val n = existingPoints.size
    val indexA = existingPoints.indexOfFirst { it.sameColumn(adj1) }
    val indexB = existingPoints.indexOfFirst { it.sameColumn(adj2) }
    if (indexA == -1 || indexB == -1) return null
    if ((indexA + 1) % n != indexB && (indexB + 1) % n != indexA) return null
    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    val newPolygon = existingPoints.toMutableList()
    newPolygon.add(insertIndex, newPoint)
    return validatedPolygon(newPolygon, limits)
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

private fun validatedPolygon(points: List<BlockPos>, limits: GeometrySizeLimits): List<BlockPos>? {
    if (validateModifiedPolygon(points, limits) != null) return null
    return points
}

private fun evaluateModifiedRectangleShape(
    geometry: RectangleGeometry,
    selectedPoints: List<BlockPos>,
    limits: GeometrySizeLimits
): ModifiedShapeEvaluation = when (selectedPoints.size) {
    0 -> ModifiedShapeEvaluation.Incomplete
    1 -> {
        val params = evaluateModifyRectangle(
            selectedPoints[0],
            listOf(geometry.west, geometry.north, geometry.east, geometry.south),
            limits
        )
        if (params == null) {
            ModifiedShapeEvaluation.Invalid(validateModifiedRectangle(selectedPoints[0], geometry, limits))
        } else {
            ModifiedShapeEvaluation.Valid(GeoShape.rectangle(GeoPoint(params[0], params[1]), GeoPoint(params[2], params[3])))
        }
    }
    else -> ModifiedShapeEvaluation.Invalid(CreationError.InsufficientPoints)
}

private fun evaluateModifiedCircleShape(
    geometry: CircleGeometry,
    selectedPoints: List<BlockPos>,
    limits: GeometrySizeLimits
): ModifiedShapeEvaluation = when (selectedPoints.size) {
    0 -> ModifiedShapeEvaluation.Incomplete
    1 -> {
        val params = evaluateModifyCircleRadius(
            selectedPoints[0],
            listOf(geometry.centerX, geometry.centerZ, geometry.radius),
            limits
        )
        if (params == null) {
            ModifiedShapeEvaluation.Invalid(validateModifiedCircleRadius(selectedPoints[0], geometry, limits))
        } else {
            ModifiedShapeEvaluation.Valid(GeoShape.circle(GeoPoint(params[0], params[1]), params[2]))
        }
    }
    2 -> {
        val params = evaluateModifyCircleCenter(
            selectedPoints[0],
            selectedPoints[1],
            listOf(geometry.centerX, geometry.centerZ, geometry.radius),
            limits
        )
        if (params == null) {
            ModifiedShapeEvaluation.Invalid(validateModifiedCircleCenter(selectedPoints[0], selectedPoints[1], geometry, limits))
        } else {
            ModifiedShapeEvaluation.Valid(GeoShape.circle(GeoPoint(params[0], params[1]), params[2]))
        }
    }
    else -> ModifiedShapeEvaluation.Invalid(CreationError.InsufficientPoints)
}

private fun evaluateModifiedPolygonShape(
    geometry: PolygonGeometry,
    selectedPoints: List<BlockPos>,
    limits: GeometrySizeLimits
): ModifiedShapeEvaluation {
    val existing = List(geometry.vertexCount) { index -> BlockPos(geometry.x(index), 0, geometry.z(index)) }
    return when (selectedPoints.size) {
        0 -> ModifiedShapeEvaluation.Incomplete
        1 -> {
            val point = selectedPoints[0]
            val polygon = if (existing.any { it.sameColumn(point) }) {
                existing.filterNot { it.sameColumn(point) }
            } else {
                evaluateModifyPolygonInsert(point, existing, limits)
            }
            when {
                polygon == null -> ModifiedShapeEvaluation.Invalid(validateModifiedPolygonSinglePoint(point, existing, limits))
                polygon.size < 3 -> ModifiedShapeEvaluation.Invalid(CreationError.InsufficientPoints)
                else -> ModifiedShapeEvaluation.Valid(GeoShape.polygon(polygon.map { GeoPoint(it.x, it.z) }))
            }
        }
        2 -> {
            val polygon = evaluateModifyPolygonReplace(selectedPoints[0], selectedPoints[1], existing, limits)
            if (polygon == null) {
                ModifiedShapeEvaluation.Invalid(validateModifiedPolygonReplace(selectedPoints[0], selectedPoints[1], existing, limits))
            } else {
                ModifiedShapeEvaluation.Valid(GeoShape.polygon(polygon.map { GeoPoint(it.x, it.z) }))
            }
        }
        3 -> {
            val polygon = evaluateModifyPolygonExplicitInsert(selectedPoints[0], selectedPoints[1], selectedPoints[2], existing, limits)
            if (polygon == null) {
                ModifiedShapeEvaluation.Invalid(validateModifiedPolygonExplicitInsert(selectedPoints[0], selectedPoints[1], selectedPoints[2], existing, limits))
            } else {
                ModifiedShapeEvaluation.Valid(GeoShape.polygon(polygon.map { GeoPoint(it.x, it.z) }))
            }
        }
        else -> ModifiedShapeEvaluation.Invalid(CreationError.InsufficientPoints)
    }
}

private fun validateModifiedRectangle(
    newPoint: BlockPos,
    geometry: RectangleGeometry,
    limits: GeometrySizeLimits
): CreationError? {
    val (west, north, east, south) = updateRectangleBounds(newPoint, listOf(geometry.west, geometry.north, geometry.east, geometry.south))
    val width = abs(east.toLong() - west)
    val length = abs(south.toLong() - north)
    return when {
        width == 0L || length == 0L -> CreationError.CoincidentPoints
        else -> checkRectangleSize(width, length, limits)
    }
}

private fun validateModifiedCircleRadius(
    newPoint: BlockPos,
    geometry: CircleGeometry,
    limits: GeometrySizeLimits
): CreationError? {
    if (newPoint.x == geometry.centerX && newPoint.z == geometry.centerZ) return CreationError.CoincidentPoints
    val radius = hypot(newPoint.x.toDouble() - geometry.centerX, newPoint.z.toDouble() - geometry.centerZ)
    return if (checkCircleSize(radius, limits)) null else CreationError.UnderSizeLimit
}

private fun validateModifiedCircleCenter(
    oldCenter: BlockPos,
    newCenter: BlockPos,
    geometry: CircleGeometry,
    limits: GeometrySizeLimits
): CreationError? {
    if (oldCenter.x != geometry.centerX || oldCenter.z != geometry.centerZ) return null
    return if (evaluatedCircle(newCenter.x, newCenter.z, geometry.radius.toDouble(), limits) == null) {
        CreationError.CoordinateRangeExceeded
    } else null
}

private fun validateModifiedPolygonSinglePoint(
    point: BlockPos,
    existing: List<BlockPos>,
    limits: GeometrySizeLimits
): CreationError? {
    if (existing.any { it.sameColumn(point) }) {
        val polygon = existing.filterNot { it.sameColumn(point) }
        return if (polygon.size < 3) CreationError.InsufficientPoints else validateModifiedPolygon(polygon, limits)
    }
    if (existing.size >= MAX_POLYGON_VERTICES) return CreationError.PolygonVertexLimitExceeded
    val (adjacentA, adjacentB) = findNearestAdjacentPoints(existing, point)
    return validateModifiedPolygonExplicitInsert(adjacentA, adjacentB, point, existing, limits)
}

private fun validateModifiedPolygonReplace(
    existingVertex: BlockPos,
    newPoint: BlockPos,
    existingPoints: List<BlockPos>,
    limits: GeometrySizeLimits
): CreationError? {
    val sourceIndex = existingPoints.indexOfFirst { it.sameColumn(existingVertex) }
    if (sourceIndex == -1) return null
    val newPolygon = existingPoints.toMutableList().apply { this[sourceIndex] = newPoint }
    return validateModifiedPolygon(newPolygon, limits)
}

private fun validateModifiedPolygonExplicitInsert(
    adj1: BlockPos,
    adj2: BlockPos,
    newPoint: BlockPos,
    existingPoints: List<BlockPos>,
    limits: GeometrySizeLimits
): CreationError? {
    if (existingPoints.size < 3) return CreationError.InsufficientPoints
    if (existingPoints.size >= MAX_POLYGON_VERTICES) return CreationError.PolygonVertexLimitExceeded
    val n = existingPoints.size
    val indexA = existingPoints.indexOfFirst { it.sameColumn(adj1) }
    val indexB = existingPoints.indexOfFirst { it.sameColumn(adj2) }
    if (indexA == -1 || indexB == -1) return null
    if ((indexA + 1) % n != indexB && (indexB + 1) % n != indexA) return null
    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    val newPolygon = existingPoints.toMutableList()
    newPolygon.add(insertIndex, newPoint)
    return validateModifiedPolygon(newPolygon, limits)
}

private fun validateModifiedPolygon(points: List<BlockPos>, limits: GeometrySizeLimits): CreationError? {
    if (!isPolygonVertexCountSupported(points.size)) return CreationError.PolygonVertexLimitExceeded
    if (points.distinctBy { it.x to it.z }.size != points.size) return CreationError.DuplicatedPoints
    if (!isConvex(points)) return CreationError.NotConvex
    return checkPolygonSize(points, limits)
}

private fun BlockPos.sameColumn(other: BlockPos): Boolean = x == other.x && z == other.z
