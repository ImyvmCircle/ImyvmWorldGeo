package com.imyvm.iwg.region

import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

sealed class CreationError {
    data object DuplicatedPoints : CreationError()
    data object InsufficientPoints : CreationError()
    data object CoincidentPoints : CreationError()
    data object UnderSizeLimit : CreationError()
    data object NotConvex : CreationError()
}

sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}

object RegionFactory {

    private const val MIN_RECTANGLE_AREA = 1.0
    private const val MIN_CIRCLE_RADIUS = 1.0

    fun createRegion(name: String, numberID: Int, selectedPositions: MutableList<BlockPos>, shapeType: Region.Companion.GeoShapeType): Result<Region, CreationError> {

        val requiredPoints = when (shapeType) {
            Region.Companion.GeoShapeType.CIRCLE, Region.Companion.GeoShapeType.RECTANGLE -> 2
            Region.Companion.GeoShapeType.POLYGON -> 3
            else -> 0
        }
        if (selectedPositions.size < requiredPoints) {
            return Result.Err(CreationError.InsufficientPoints)
        }

        val distinctPositions = selectedPositions.distinct()
        if (distinctPositions.size != selectedPositions.size) {
            return Result.Err(CreationError.DuplicatedPoints)
        }

        val geoShape: Region.Companion.GeoShape? = when (shapeType) {
            Region.Companion.GeoShapeType.RECTANGLE -> {
                val pos1 = selectedPositions[0]
                val pos2 = selectedPositions[1]

                if (pos1.x == pos2.x || pos1.z == pos2.z) {
                    return Result.Err(CreationError.CoincidentPoints)
                }

                val width = maxOf(pos1.x, pos2.x) - minOf(pos1.x, pos2.x)
                val length = maxOf(pos1.z, pos2.z) - minOf(pos1.z, pos2.z)
                if ((width * length).toDouble() < MIN_RECTANGLE_AREA) {
                    return Result.Err(CreationError.UnderSizeLimit)
                }

                Region.Companion.GeoShape().apply {
                    geoShapeType = Region.Companion.GeoShapeType.RECTANGLE
                    shapeParameter = mutableListOf(
                        minOf(pos1.x, pos2.x), minOf(pos1.z, pos2.z),
                        maxOf(pos1.x, pos2.x), maxOf(pos1.z, pos2.z)
                    )
                }
            }
            Region.Companion.GeoShapeType.CIRCLE -> {
                val centerPos = selectedPositions[0]
                val pointOnCircumference = selectedPositions[1]

                if (centerPos.x == pointOnCircumference.x && centerPos.z == pointOnCircumference.z) {
                    return Result.Err(CreationError.CoincidentPoints)
                }

                val dx = pointOnCircumference.x - centerPos.x
                val dz = pointOnCircumference.z - centerPos.z
                val radius = sqrt((dx * dx + dz * dz).toDouble())

                if (radius < MIN_CIRCLE_RADIUS) {
                    return Result.Err(CreationError.UnderSizeLimit)
                }

                Region.Companion.GeoShape().apply {
                    geoShapeType = Region.Companion.GeoShapeType.CIRCLE
                    shapeParameter = mutableListOf(centerPos.x, centerPos.z, radius.toInt())
                }
            }
            Region.Companion.GeoShapeType.POLYGON -> {
                if (!isConvex(selectedPositions)) {
                    return Result.Err(CreationError.NotConvex)
                }
                Region.Companion.GeoShape().apply {
                    geoShapeType = Region.Companion.GeoShapeType.POLYGON
                    shapeParameter = selectedPositions.flatMap { listOf(it.x, it.z) }.toMutableList()
                }
            }
            else -> {
                return Result.Err(CreationError.InsufficientPoints)
            }
        }

        val geoScope = Region.Companion.GeoScope().apply {
            scopeName = "main_scope"
            this.geoShape = geoShape
        }

        val newRegion = Region().apply {
            this.name = name
            this.numberID = numberID
            geometryScope.add(geoScope)
        }

        return Result.Ok(newRegion)
    }

    private fun isConvex(positions: List<BlockPos>): Boolean {
        if (positions.size < 3) return false
        var sign = 0
        val n = positions.size
        for (i in 0 until n) {
            val p1 = positions[i]
            val p2 = positions[(i + 1) % n]
            val p3 = positions[(i + 2) % n]
            val crossProduct = (p2.x - p1.x) * (p3.z - p2.z) - (p2.z - p1.z) * (p3.x - p2.x)
            if (crossProduct != 0) {
                val currentSign = if (crossProduct > 0) 1 else -1
                if (sign == 0) {
                    sign = currentSign
                } else if (currentSign != sign) {
                    return false
                }
            }
        }
        return true
    }
}