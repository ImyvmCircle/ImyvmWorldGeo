package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.circleContainsPoint
import com.imyvm.iwg.util.geo.generateShapePointSequence
import com.imyvm.iwg.util.geo.iterateCirclePointSequence
import com.imyvm.iwg.util.geo.iterateRectanglePointSequence
import com.imyvm.iwg.util.geo.isConvexCoordinates
import java.math.BigInteger
import kotlin.math.abs

internal sealed interface ShapeGeometry {
    val type: GeoShapeType

    fun toLegacyParameters(): MutableList<Int>

    fun containsPoint(x: Int, z: Int): Boolean

    fun calculateArea(): Double

    fun pointSequence(): Sequence<Pair<Int, Int>>

    companion object {
        fun from(type: GeoShapeType, parameters: List<Int>): ShapeGeometry = when (type) {
            GeoShapeType.CIRCLE -> {
                require(parameters.size == 3) { "circle requires center x/z and radius" }
                CircleGeometry(parameters[0], parameters[1], parameters[2])
            }
            GeoShapeType.RECTANGLE -> {
                require(parameters.size == 4) { "rectangle requires west/north/east/south" }
                RectangleGeometry(parameters[0], parameters[1], parameters[2], parameters[3])
            }
            GeoShapeType.POLYGON -> PolygonGeometry(parameters.toIntArray())
            GeoShapeType.UNKNOWN -> {
                require(parameters.isEmpty()) { "unknown shape must not have parameters" }
                UnknownGeometry
            }
        }
    }
}

internal data class CircleGeometry(
    val centerX: Int,
    val centerZ: Int,
    val radius: Int
) : ShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.CIRCLE

    init {
        require(radius >= 0) { "circle radius must not be negative" }
        Math.subtractExact(centerX, radius)
        Math.addExact(centerX, radius)
        Math.subtractExact(centerZ, radius)
        Math.addExact(centerZ, radius)
    }

    override fun toLegacyParameters(): MutableList<Int> = mutableListOf(centerX, centerZ, radius)

    override fun containsPoint(x: Int, z: Int): Boolean =
        circleContainsPoint(x.toLong() - centerX, z.toLong() - centerZ, radius)

    override fun calculateArea(): Double = Math.PI * radius.toDouble() * radius

    override fun pointSequence(): Sequence<Pair<Int, Int>> = iterateCirclePointSequence(centerX, centerZ, radius)
}

internal data class RectangleGeometry(
    val west: Int,
    val north: Int,
    val east: Int,
    val south: Int
) : ShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.RECTANGLE

    init {
        require(west <= east && north <= south) { "rectangle bounds are inverted" }
    }

    override fun toLegacyParameters(): MutableList<Int> = mutableListOf(west, north, east, south)

    override fun containsPoint(x: Int, z: Int): Boolean = x in west..east && z in north..south

    override fun calculateArea(): Double = (east.toDouble() - west) * (south.toDouble() - north)

    override fun pointSequence(): Sequence<Pair<Int, Int>> = iterateRectanglePointSequence(west, north, east, south)
}

internal class PolygonGeometry(parameters: IntArray) : ShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.POLYGON
    private val coordinates: IntArray = parameters.copyOf()
    private val bounds: IntArray

    val vertexCount: Int
        get() = coordinates.size / 2

    init {
        require(coordinates.size >= 6 && coordinates.size % 2 == 0) {
            "polygon requires at least three coordinate pairs"
        }
        require(hasDistinctVertices()) { "polygon vertices must be distinct" }
        require(isConvexCoordinates(vertexCount, ::x, ::z)) { "polygon must be convex and non-degenerate" }
        bounds = calculateBounds()
    }

    fun x(index: Int): Int = coordinates[index * 2]

    fun z(index: Int): Int = coordinates[index * 2 + 1]

    override fun toLegacyParameters(): MutableList<Int> = coordinates.toMutableList()

    override fun containsPoint(x: Int, z: Int): Boolean {
        var winding = 0
        var previous = vertexCount - 1
        for (current in 0 until vertexCount) {
            val currentX = x(current)
            val currentZ = z(current)
            val previousX = x(previous)
            val previousZ = z(previous)
            val orientation = orientationSign(previousX, previousZ, currentX, currentZ, x, z)
            if (orientation == 0 &&
                x in minOf(currentX, previousX)..maxOf(currentX, previousX) &&
                z in minOf(currentZ, previousZ)..maxOf(currentZ, previousZ)
            ) {
                return true
            }

            if (previousZ <= z) {
                if (currentZ > z && orientation > 0) winding++
            } else if (currentZ <= z && orientation < 0) {
                winding--
            }
            previous = current
        }
        return winding != 0
    }

    override fun calculateArea(): Double {
        var twiceArea = 0.0
        for (index in 0 until vertexCount) {
            val next = (index + 1) % vertexCount
            twiceArea += x(index).toDouble() * z(next) - x(next).toDouble() * z(index)
        }
        return abs(twiceArea) / 2.0
    }

    override fun pointSequence(): Sequence<Pair<Int, Int>> =
        generateShapePointSequence(bounds[0], bounds[2], bounds[1], bounds[3], ::containsPoint)

    private fun hasDistinctVertices(): Boolean {
        val seen = HashSet<Long>(vertexCount)
        for (index in 0 until vertexCount) {
            val packed = (x(index).toLong() shl 32) xor (z(index).toLong() and 0xffffffffL)
            if (!seen.add(packed)) return false
        }
        return true
    }

    private fun calculateBounds(): IntArray {
        var minX = x(0)
        var minZ = z(0)
        var maxX = minX
        var maxZ = minZ
        for (index in 1 until vertexCount) {
            minX = minOf(minX, x(index))
            minZ = minOf(minZ, z(index))
            maxX = maxOf(maxX, x(index))
            maxZ = maxOf(maxZ, z(index))
        }
        return intArrayOf(minX, minZ, maxX, maxZ)
    }
}

internal data object UnknownGeometry : ShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.UNKNOWN

    override fun toLegacyParameters(): MutableList<Int> = mutableListOf()

    override fun containsPoint(x: Int, z: Int): Boolean = false

    override fun calculateArea(): Double = 0.0

    override fun pointSequence(): Sequence<Pair<Int, Int>> = emptySequence()
}

private fun exactCross(ax: Long, az: Long, bx: Long, bz: Long): BigInteger =
    BigInteger.valueOf(ax).multiply(BigInteger.valueOf(bz))
        .subtract(BigInteger.valueOf(az).multiply(BigInteger.valueOf(bx)))

private fun orientationSign(ax: Int, az: Int, bx: Int, bz: Int, px: Int, pz: Int): Int {
    val edgeX = bx.toLong() - ax
    val edgeZ = bz.toLong() - az
    val pointX = px.toLong() - ax
    val pointZ = pz.toLong() - az
    val firstProduct = edgeX.toDouble() * pointZ
    val secondProduct = edgeZ.toDouble() * pointX
    val determinant = firstProduct - secondProduct
    val fallbackThreshold = (abs(firstProduct) + abs(secondProduct)) * 1e-12
    if (abs(determinant) > fallbackThreshold) return determinant.compareTo(0.0)
    return exactCross(edgeX, edgeZ, pointX, pointZ).signum()
}
