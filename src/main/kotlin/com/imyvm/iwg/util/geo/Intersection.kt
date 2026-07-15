package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import java.math.BigInteger

data class VertexInsideInfo(val index: Int, val x: Int, val z: Int)

data class IntersectionDetail(
    val regionName: String,
    val scopeName: String,
    val shape: GeoShape,
    val verticesInside: List<VertexInsideInfo>
)

fun checkIntersection(
    newShape: GeoShape,
    existingScopes: List<Pair<GeoScope, String>>
): List<IntersectionDetail> {
    val newShapeVertices = getShapeVertices(newShape)
    val result = mutableListOf<IntersectionDetail>()
    for ((scope, regionName) in existingScopes) {
        val existingShape = scope.geoShape ?: continue
        if (geoOverlap(newShape, existingShape)) {
            val verticesInside = newShapeVertices.mapIndexedNotNull { index, (x, z) ->
                if (existingShape.containsPoint(x, z)) VertexInsideInfo(index + 1, x, z) else null
            }
            result.add(IntersectionDetail(regionName, scope.scopeName, existingShape, verticesInside))
        }
    }
    return result
}

private fun getShapeVertices(shape: GeoShape): List<Pair<Int, Int>> =
    when (val geometry = shape.typedGeometry) {
        is PolygonGeometry -> List(geometry.vertexCount) { geometry.x(it) to geometry.z(it) }
        is RectangleGeometry -> listOf(
            geometry.west to geometry.north,
            geometry.east to geometry.north,
            geometry.east to geometry.south,
            geometry.west to geometry.south
        )
        else -> emptyList()
    }

private fun geoOverlap(firstShape: GeoShape, secondShape: GeoShape): Boolean =
    when (val first = firstShape.typedGeometry) {
        is RectangleGeometry -> when (val second = secondShape.typedGeometry) {
            is RectangleGeometry -> rectangleOverlapRectangle(first, second)
            is CircleGeometry -> rectangleOverlapCircle(first, second)
            is PolygonGeometry -> rectangleOverlapPolygon(first, second)
            UnknownGeometry -> false
        }
        is CircleGeometry -> when (val second = secondShape.typedGeometry) {
            is RectangleGeometry -> rectangleOverlapCircle(second, first)
            is CircleGeometry -> circleOverlapCircle(first, second)
            is PolygonGeometry -> polygonOverlapCircle(second, first)
            UnknownGeometry -> false
        }
        is PolygonGeometry -> when (val second = secondShape.typedGeometry) {
            is RectangleGeometry -> rectangleOverlapPolygon(second, first)
            is CircleGeometry -> polygonOverlapCircle(first, second)
            is PolygonGeometry -> polygonOverlapPolygon(first, second)
            UnknownGeometry -> false
        }
        UnknownGeometry -> false
    }

private fun rectangleOverlapRectangle(first: RectangleGeometry, second: RectangleGeometry): Boolean =
    first.west <= second.east && first.east >= second.west &&
        first.north <= second.south && first.south >= second.north

private fun rectangleOverlapCircle(rectangle: RectangleGeometry, circle: CircleGeometry): Boolean {
    val closestX = circle.centerX.coerceIn(rectangle.west, rectangle.east)
    val closestZ = circle.centerZ.coerceIn(rectangle.north, rectangle.south)
    return distanceWithinRadius(
        circle.centerX.toLong() - closestX,
        circle.centerZ.toLong() - closestZ,
        circle.radius.toLong()
    )
}

private fun circleOverlapCircle(first: CircleGeometry, second: CircleGeometry): Boolean {
    val radiusSum = first.radius.toLong() + second.radius
    return distanceWithinRadius(
        first.centerX.toLong() - second.centerX,
        first.centerZ.toLong() - second.centerZ,
        radiusSum
    )
}

private fun rectangleOverlapPolygon(rectangle: RectangleGeometry, polygon: PolygonGeometry): Boolean =
    polygonsIntersect(
        4,
        { if (it == 0 || it == 3) rectangle.west else rectangle.east },
        { if (it < 2) rectangle.north else rectangle.south },
        polygon.vertexCount,
        polygon::x,
        polygon::z
    )

private fun polygonOverlapPolygon(first: PolygonGeometry, second: PolygonGeometry): Boolean =
    polygonsIntersect(
        first.vertexCount,
        first::x,
        first::z,
        second.vertexCount,
        second::x,
        second::z
    )

private fun polygonOverlapCircle(polygon: PolygonGeometry, circle: CircleGeometry): Boolean {
    for (index in 0 until polygon.vertexCount) {
        if (distanceWithinRadius(
                polygon.x(index).toLong() - circle.centerX,
                polygon.z(index).toLong() - circle.centerZ,
                circle.radius.toLong()
            )
        ) {
            return true
        }
    }
    if (polygon.containsPoint(circle.centerX, circle.centerZ)) return true

    for (index in 0 until polygon.vertexCount) {
        val next = (index + 1) % polygon.vertexCount
        if (segmentCircleIntersects(
                polygon.x(index),
                polygon.z(index),
                polygon.x(next),
                polygon.z(next),
                circle
            )
        ) {
            return true
        }
    }
    return false
}

private fun segmentCircleIntersects(
    startX: Int,
    startZ: Int,
    endX: Int,
    endZ: Int,
    circle: CircleGeometry
): Boolean {
    val edgeX = endX.toLong() - startX
    val edgeZ = endZ.toLong() - startZ
    val centerX = circle.centerX.toLong() - startX
    val centerZ = circle.centerZ.toLong() - startZ
    val edgeLengthSquared = exactDot(edgeX, edgeZ, edgeX, edgeZ)
    if (edgeLengthSquared.signum() == 0) {
        return distanceWithinRadius(centerX, centerZ, circle.radius.toLong())
    }

    val projection = exactDot(centerX, centerZ, edgeX, edgeZ)
    if (projection.signum() <= 0) {
        return distanceWithinRadius(centerX, centerZ, circle.radius.toLong())
    }
    if (projection >= edgeLengthSquared) {
        return distanceWithinRadius(
            circle.centerX.toLong() - endX,
            circle.centerZ.toLong() - endZ,
            circle.radius.toLong()
        )
    }

    val cross = exactConvexityCross(edgeX, edgeZ, centerX, centerZ)
    val radiusSquared = BigInteger.valueOf(circle.radius.toLong()).pow(2)
    return cross.pow(2) <= radiusSquared.multiply(edgeLengthSquared)
}

private fun distanceWithinRadius(dx: Long, dz: Long, radius: Long): Boolean {
    if (dx < -radius || dx > radius || dz < -radius || dz > radius) return false
    if (radius <= Int.MAX_VALUE) return circleContainsPoint(dx, dz, radius.toInt())

    val distanceSquared = exactDot(dx, dz, dx, dz)
    return distanceSquared <= BigInteger.valueOf(radius).pow(2)
}

private fun exactDot(ax: Long, az: Long, bx: Long, bz: Long): BigInteger =
    BigInteger.valueOf(ax).multiply(BigInteger.valueOf(bx))
        .add(BigInteger.valueOf(az).multiply(BigInteger.valueOf(bz)))

private fun polygonsIntersect(
    firstCount: Int,
    firstX: (Int) -> Int,
    firstZ: (Int) -> Int,
    secondCount: Int,
    secondX: (Int) -> Int,
    secondZ: (Int) -> Int
): Boolean {
    for (firstIndex in 0 until firstCount) {
        val firstNext = (firstIndex + 1) % firstCount
        for (secondIndex in 0 until secondCount) {
            val secondNext = (secondIndex + 1) % secondCount
            if (segmentsIntersect(
                    firstX(firstIndex),
                    firstZ(firstIndex),
                    firstX(firstNext),
                    firstZ(firstNext),
                    secondX(secondIndex),
                    secondZ(secondIndex),
                    secondX(secondNext),
                    secondZ(secondNext)
                )
            ) {
                return true
            }
        }
    }
    return polygonContainsPointCoordinates(
        secondCount,
        secondX,
        secondZ,
        firstX(0),
        firstZ(0)
    ) || polygonContainsPointCoordinates(
        firstCount,
        firstX,
        firstZ,
        secondX(0),
        secondZ(0)
    )
}

internal fun segmentsIntersect(
    firstStartX: Int,
    firstStartZ: Int,
    firstEndX: Int,
    firstEndZ: Int,
    secondStartX: Int,
    secondStartZ: Int,
    secondEndX: Int,
    secondEndZ: Int
): Boolean {
    val firstStartSide = orientationSign(
        firstStartX,
        firstStartZ,
        firstEndX,
        firstEndZ,
        secondStartX,
        secondStartZ
    )
    val firstEndSide = orientationSign(
        firstStartX,
        firstStartZ,
        firstEndX,
        firstEndZ,
        secondEndX,
        secondEndZ
    )
    val secondStartSide = orientationSign(
        secondStartX,
        secondStartZ,
        secondEndX,
        secondEndZ,
        firstStartX,
        firstStartZ
    )
    val secondEndSide = orientationSign(
        secondStartX,
        secondStartZ,
        secondEndX,
        secondEndZ,
        firstEndX,
        firstEndZ
    )

    if (firstStartSide * firstEndSide < 0 && secondStartSide * secondEndSide < 0) return true
    if (firstStartSide == 0 && pointOnSegment(firstStartX, firstStartZ, secondStartX, secondStartZ, firstEndX, firstEndZ)) return true
    if (firstEndSide == 0 && pointOnSegment(firstStartX, firstStartZ, secondEndX, secondEndZ, firstEndX, firstEndZ)) return true
    if (secondStartSide == 0 && pointOnSegment(secondStartX, secondStartZ, firstStartX, firstStartZ, secondEndX, secondEndZ)) return true
    return secondEndSide == 0 && pointOnSegment(
        secondStartX,
        secondStartZ,
        firstEndX,
        firstEndZ,
        secondEndX,
        secondEndZ
    )
}

private fun pointOnSegment(
    startX: Int,
    startZ: Int,
    pointX: Int,
    pointZ: Int,
    endX: Int,
    endZ: Int
): Boolean =
    pointX in minOf(startX, endX)..maxOf(startX, endX) &&
        pointZ in minOf(startZ, endZ)..maxOf(startZ, endZ)
