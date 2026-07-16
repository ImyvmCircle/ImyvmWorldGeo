package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.circleContainsPoint
import com.imyvm.iwg.util.geo.isConvexCoordinates
import com.imyvm.iwg.util.geo.polygonContainsPointCoordinates
import com.imyvm.iwg.util.geo.polygonTwiceArea

internal const val MAX_POLYGON_VERTICES = 256

internal fun isPolygonVertexCountSupported(vertexCount: Int): Boolean =
    vertexCount <= MAX_POLYGON_VERTICES

internal data class ShapeBounds(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int
) {
    init {
        require(minX <= maxX && minZ <= maxZ) { "shape bounds are inverted" }
    }
}

internal sealed interface ShapeGeometry {
    val type: GeoShapeType

    fun toLegacyParameters(): MutableList<Int>

    fun containsPoint(x: Int, z: Int): Boolean

    fun calculateArea(): Double

    fun representativePoint(): Pair<Int, Int>?

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

internal sealed interface BoundedShapeGeometry : ShapeGeometry {
    val containmentWorkUnits: Int

    fun bounds(): ShapeBounds
}

internal data class CircleGeometry(
    val centerX: Int,
    val centerZ: Int,
    val radius: Int
) : BoundedShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.CIRCLE
    override val containmentWorkUnits: Int = 1

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

    override fun representativePoint(): Pair<Int, Int> = centerX to centerZ

    override fun bounds(): ShapeBounds = ShapeBounds(
        Math.subtractExact(centerX, radius),
        Math.subtractExact(centerZ, radius),
        Math.addExact(centerX, radius),
        Math.addExact(centerZ, radius)
    )
}

internal data class RectangleGeometry(
    val west: Int,
    val north: Int,
    val east: Int,
    val south: Int
) : BoundedShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.RECTANGLE
    override val containmentWorkUnits: Int = 1

    init {
        require(west <= east && north <= south) { "rectangle bounds are inverted" }
    }

    override fun toLegacyParameters(): MutableList<Int> = mutableListOf(west, north, east, south)

    override fun containsPoint(x: Int, z: Int): Boolean = x in west..east && z in north..south

    override fun calculateArea(): Double = (east.toDouble() - west) * (south.toDouble() - north)

    override fun representativePoint(): Pair<Int, Int> =
        (west.toLong() + (east.toLong() - west) / 2).toInt() to
            (north.toLong() + (south.toLong() - north) / 2).toInt()

    override fun bounds(): ShapeBounds = ShapeBounds(west, north, east, south)
}

internal class PolygonGeometry(parameters: IntArray) : BoundedShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.POLYGON
    private val coordinates: IntArray = parameters.copyOf()

    val vertexCount: Int
        get() = coordinates.size / 2

    override val containmentWorkUnits: Int
        get() = vertexCount

    init {
        require(coordinates.size >= 6 && coordinates.size % 2 == 0) {
            "polygon requires at least three coordinate pairs"
        }
        require(vertexCount <= MAX_POLYGON_VERTICES) {
            "polygon must not exceed $MAX_POLYGON_VERTICES vertices"
        }
        require(hasDistinctVertices()) { "polygon vertices must be distinct" }
        require(isConvexCoordinates(vertexCount, ::x, ::z)) { "polygon must be convex and non-degenerate" }
    }

    fun x(index: Int): Int = coordinates[index * 2]

    fun z(index: Int): Int = coordinates[index * 2 + 1]

    override fun toLegacyParameters(): MutableList<Int> = coordinates.toMutableList()

    override fun containsPoint(x: Int, z: Int): Boolean {
        return polygonContainsPointCoordinates(vertexCount, ::x, ::z, x, z)
    }

    override fun calculateArea(): Double = polygonTwiceArea(vertexCount, ::x, ::z).toDouble() / 2.0

    override fun representativePoint(): Pair<Int, Int> = x(0) to z(0)

    override fun bounds(): ShapeBounds {
        var minX = x(0)
        var minZ = z(0)
        var maxX = minX
        var maxZ = minZ
        for (index in 1 until vertexCount) {
            val currentX = x(index)
            val currentZ = z(index)
            minX = minOf(minX, currentX)
            minZ = minOf(minZ, currentZ)
            maxX = maxOf(maxX, currentX)
            maxZ = maxOf(maxZ, currentZ)
        }
        return ShapeBounds(minX, minZ, maxX, maxZ)
    }

    private fun hasDistinctVertices(): Boolean {
        val seen = HashSet<Long>(vertexCount)
        for (index in 0 until vertexCount) {
            val packed = (x(index).toLong() shl 32) xor (z(index).toLong() and 0xffffffffL)
            if (!seen.add(packed)) return false
        }
        return true
    }

}

internal data object UnknownGeometry : ShapeGeometry {
    override val type: GeoShapeType = GeoShapeType.UNKNOWN

    override fun toLegacyParameters(): MutableList<Int> = mutableListOf()

    override fun containsPoint(x: Int, z: Int): Boolean = false

    override fun calculateArea(): Double = 0.0

    override fun representativePoint(): Pair<Int, Int>? = null
}
