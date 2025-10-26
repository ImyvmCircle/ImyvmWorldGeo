package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.geo.*
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.sqrt

object RegionFactory {

    fun createRegion(
        name: String,
        numberID: Int,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<Region, CreationError> {
        val mainScopeResult = createScope("main_scope", selectedPositions, shapeType)

        if (mainScopeResult is Result.Err) return mainScopeResult

        val mainScope = (mainScopeResult as Result.Ok).value
        val geometryScope = mutableListOf<GeoScope>()
        geometryScope.add(mainScope)

        val newRegion = Region(name, numberID, geometryScope)

        return Result.Ok(newRegion)
    }

    fun createScope(
        scopeName: String,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoScope, CreationError> {
        val geoShapeResult = createGeoShape(selectedPositions, shapeType)

        if (geoShapeResult is Result.Err) {
            return Result.Err(geoShapeResult.error)
        }

        val geoScope = GeoScope(scopeName, (geoShapeResult as Result.Ok).value)

        return Result.Ok(geoScope)
    }

    private fun createGeoShape(
        positions: List<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoShape, CreationError> {
        val requiredPoints = requiredPoints(shapeType)
        if (positions.size < requiredPoints) {
            return Result.Err(CreationError.InsufficientPoints)
        }

        val geoShapeResult = when (shapeType) {
            GeoShapeType.RECTANGLE -> createRectangle(positions)
            GeoShapeType.CIRCLE -> createCircle(positions)
            GeoShapeType.POLYGON -> createPolygon(positions)
            else -> Result.Err(CreationError.InsufficientPoints)
        }

        if (geoShapeResult is Result.Err) return geoShapeResult

        val geoShape = (geoShapeResult as Result.Ok).value

        val existingScopes = RegionDatabase.getRegionList().flatMap { it.geometryScope }
        if (checkIntersection(geoShape, existingScopes)) {
            return Result.Err(CreationError.IntersectionBetweenScopes)
        }

        return Result.Ok(geoShape)
    }

    private fun requiredPoints(shapeType: GeoShapeType): Int =
        when (shapeType) {
            GeoShapeType.CIRCLE,
            GeoShapeType.RECTANGLE -> 2
            GeoShapeType.POLYGON -> 3
            else -> 0
        }

    private fun createRectangle(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val pos1 = positions[0]
        val pos2 = positions[1]

        if (pos1 == pos2) return Result.Err(CreationError.DuplicatedPoints)
        if (pos1.x == pos2.x || pos1.z == pos2.z) return Result.Err(CreationError.CoincidentPoints)

        val width = abs(pos1.x - pos2.x)
        val length = abs(pos1.z - pos2.z)
        val error = checkRectangleSize(width, length)
        if (error != null) return Result.Err(error)

        val west  = minOf(pos1.x, pos2.x)
        val east  = maxOf(pos1.x, pos2.x)
        val north = minOf(pos1.z, pos2.z)
        val south = maxOf(pos1.z, pos2.z)

        return Result.Ok(
            GeoShape(GeoShapeType.RECTANGLE, mutableListOf(west, north, east, south))
        )
    }

    private fun createCircle(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val center = positions[0]
        val circumference = positions[1]

        if (center == circumference) return Result.Err(CreationError.DuplicatedPoints)

        val dx = circumference.x - center.x
        val dz = circumference.z - center.z
        val radius = sqrt((dx * dx + dz * dz).toDouble())
        if (!checkCircleSize(radius)) return Result.Err(CreationError.UnderSizeLimit)

        return Result.Ok(
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(center.x, center.z, radius.toInt()))
        )
    }

    private fun createPolygon(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val distinct = positions.distinct()
        if (distinct.size != positions.size) return Result.Err(CreationError.DuplicatedPoints)
        if (!isConvex(positions)) return Result.Err(CreationError.NotConvex)
        val area = polygonArea(positions)
        val error = checkPolygonSize(positions, area)
        if (error != null) return Result.Err(error)

        return Result.Ok(
            GeoShape(
                geoShapeType = GeoShapeType.POLYGON,
                positions.flatMap { listOf(it.x, it.z) }.toMutableList()
            )
        )
    }

    private fun polygonArea(positions: List<BlockPos>): Double {
        var area = 0.0
        val n = positions.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += (positions[i].x * positions[j].z - positions[j].x * positions[i].z)
        }
        return abs(area) / 2.0
    }
}

sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}