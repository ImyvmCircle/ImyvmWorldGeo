package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry

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
    return kotlin.math.hypot(
        circle.centerX.toDouble() - closestX,
        circle.centerZ.toDouble() - closestZ
    ) <= circle.radius
}

private fun circleOverlapCircle(first: CircleGeometry, second: CircleGeometry): Boolean {
    val radiusSum = first.radius.toLong() + second.radius
    return kotlin.math.hypot(
        first.centerX.toDouble() - second.centerX,
        first.centerZ.toDouble() - second.centerZ
    ) <= radiusSum
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
        if (kotlin.math.hypot(
                polygon.x(index).toDouble() - circle.centerX,
                polygon.z(index).toDouble() - circle.centerZ
            ) <= circle.radius
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
    val dx = endX.toDouble() - startX
    val dz = endZ.toDouble() - startZ
    val fx = startX.toDouble() - circle.centerX
    val fz = startZ.toDouble() - circle.centerZ
    val a = dx * dx + dz * dz
    if (a == 0.0) return kotlin.math.hypot(fx, fz) <= circle.radius
    val b = 2 * (fx * dx + fz * dz)
    val c = fx * fx + fz * fz - circle.radius.toDouble() * circle.radius
    val discriminant = b * b - 4 * a * c
    if (discriminant < 0) return false
    val squareRoot = kotlin.math.sqrt(discriminant)
    val first = (-b - squareRoot) / (2 * a)
    val second = (-b + squareRoot) / (2 * a)
    return first in 0.0..1.0 || second in 0.0..1.0
}

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
