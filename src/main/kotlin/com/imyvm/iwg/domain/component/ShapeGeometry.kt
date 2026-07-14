package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.circleContainsPoint
import com.imyvm.iwg.util.geo.generateShapePointSequence
import com.imyvm.iwg.util.geo.iterateCirclePointSequence
import com.imyvm.iwg.util.geo.iterateRectanglePointSequence
import com.imyvm.iwg.util.geo.isConvexCoordinates
import com.imyvm.iwg.util.geo.polygonContainsPointCoordinates
import com.imyvm.iwg.util.geo.polygonTwiceArea

internal const val MAX_POLYGON_VERTICES = 256

internal fun isPolygonVertexCountSupported(vertexCount: Int): Boolean =
    vertexCount <= MAX_POLYGON_VERTICES

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
            GeoShapeType.POLYGON -> {
                require(parameters.size <= MAX_POLYGON_VERTICES * 2) {
                    "polygon must not exceed $MAX_POLYGON_VERTICES vertices"
                }
                PolygonGeometry(parameters.toIntArray())
            }
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
        require(vertexCount <= MAX_POLYGON_VERTICES) {
            "polygon must not exceed $MAX_POLYGON_VERTICES vertices"
        }
        require(hasDistinctVertices()) { "polygon vertices must be distinct" }
        require(isConvexCoordinates(vertexCount, ::x, ::z)) { "polygon must be convex and non-degenerate" }
        bounds = calculateBounds()
    }

    fun x(index: Int): Int = coordinates[index * 2]

    fun z(index: Int): Int = coordinates[index * 2 + 1]

    override fun toLegacyParameters(): MutableList<Int> = coordinates.toMutableList()

    override fun containsPoint(x: Int, z: Int): Boolean {
        return polygonContainsPointCoordinates(vertexCount, ::x, ::z, x, z)
    }

    override fun calculateArea(): Double = polygonTwiceArea(vertexCount, ::x, ::z).toDouble() / 2.0

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
