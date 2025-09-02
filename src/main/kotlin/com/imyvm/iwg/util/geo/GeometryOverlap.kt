package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.Region
import net.minecraft.util.math.BlockPos

fun checkIntersection(
    newShape: Region.Companion.GeoShape,
    existingScopes: List<Region.Companion.GeoScope>
): Boolean {
    for (scope in existingScopes) {
        val existingGeo = scope.geoShape ?: continue
        if (geoOverlap(newShape, existingGeo)) return true
    }
    return false
}

private fun geoOverlap(a: Region.Companion.GeoShape, b: Region.Companion.GeoShape): Boolean {
    return when (a.geoShapeType) {
        Region.Companion.GeoShapeType.RECTANGLE -> when (b.geoShapeType) {
            Region.Companion.GeoShapeType.RECTANGLE -> rectOverlapRect(a, b)
            Region.Companion.GeoShapeType.CIRCLE -> rectOverlapCircle(a, b)
            Region.Companion.GeoShapeType.POLYGON -> rectOverlapPolygon(a, b)
            else -> false
        }
        Region.Companion.GeoShapeType.CIRCLE -> when (b.geoShapeType) {
            Region.Companion.GeoShapeType.RECTANGLE -> rectOverlapCircle(b, a)
            Region.Companion.GeoShapeType.CIRCLE -> circleOverlapCircle(a, b)
            Region.Companion.GeoShapeType.POLYGON -> polygonOverlapCircle(b, a)
            else -> false
        }
        Region.Companion.GeoShapeType.POLYGON -> when (b.geoShapeType) {
            Region.Companion.GeoShapeType.RECTANGLE -> rectOverlapPolygon(b, a)
            Region.Companion.GeoShapeType.CIRCLE -> polygonOverlapCircle(a, b)
            Region.Companion.GeoShapeType.POLYGON -> polygonOverlapPolygon(a, b)
            else -> false
        }
        else -> false
    }
}

private fun rectOverlapRect(a: Region.Companion.GeoShape, b: Region.Companion.GeoShape): Boolean {
    val ax1 = a.shapeParameter[0]
    val az1 = a.shapeParameter[1]
    val ax2 = a.shapeParameter[2]
    val az2 = a.shapeParameter[3]

    val bx1 = b.shapeParameter[0]
    val bz1 = b.shapeParameter[1]
    val bx2 = b.shapeParameter[2]
    val bz2 = b.shapeParameter[3]

    return ax1 < bx2 && ax2 > bx1 && az1 < bz2 && az2 > bz1
}

private fun rectOverlapCircle(rect: Region.Companion.GeoShape, circle: Region.Companion.GeoShape): Boolean {
    val rx1 = rect.shapeParameter[0]
    val rz1 = rect.shapeParameter[1]
    val rx2 = rect.shapeParameter[2]
    val rz2 = rect.shapeParameter[3]

    val cx = circle.shapeParameter[0]
    val cz = circle.shapeParameter[1]
    val r = circle.shapeParameter[2]

    val closestX = cx.coerceIn(rx1, rx2)
    val closestZ = cz.coerceIn(rz1, rz2)

    val dx = cx - closestX
    val dz = cz - closestZ
    return dx * dx + dz * dz <= r * r
}

private fun circleOverlapCircle(a: Region.Companion.GeoShape, b: Region.Companion.GeoShape): Boolean {
    val dx = a.shapeParameter[0] - b.shapeParameter[0]
    val dz = a.shapeParameter[1] - b.shapeParameter[1]
    val rSum = a.shapeParameter[2] + b.shapeParameter[2]
    return dx * dx + dz * dz <= rSum * rSum
}

private fun rectOverlapPolygon(rect: Region.Companion.GeoShape, poly: Region.Companion.GeoShape): Boolean {
    val rectPoly = listOf(
        BlockPos(rect.shapeParameter[0], 0, rect.shapeParameter[1]),
        BlockPos(rect.shapeParameter[2], 0, rect.shapeParameter[1]),
        BlockPos(rect.shapeParameter[2], 0, rect.shapeParameter[3]),
        BlockPos(rect.shapeParameter[0], 0, rect.shapeParameter[3])
    )
    return polygonsIntersect(rectPoly, poly.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) })
}

private fun polygonOverlapPolygon(a: Region.Companion.GeoShape, b: Region.Companion.GeoShape): Boolean {
    return polygonsIntersect(
        a.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) },
        b.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) }
    )
}

private fun polygonOverlapCircle(poly: Region.Companion.GeoShape, circle: Region.Companion.GeoShape): Boolean {
    val cx = circle.shapeParameter[0]
    val cz = circle.shapeParameter[1]
    val r = circle.shapeParameter[2]
    val polyPoints = poly.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) }
    return polyPoints.any {
        val dx = it.x - cx
        val dz = it.z - cz
        dx * dx + dz * dz <= r * r
    }
}

private fun polygonsIntersect(a: List<BlockPos>, b: List<BlockPos>): Boolean {
    val aMinX = a.minOf { it.x }
    val aMaxX = a.maxOf { it.x }
    val aMinZ = a.minOf { it.z }
    val aMaxZ = a.maxOf { it.z }

    val bMinX = b.minOf { it.x }
    val bMaxX = b.maxOf { it.x }
    val bMinZ = b.minOf { it.z }
    val bMaxZ = b.maxOf { it.z }

    return aMinX < bMaxX && aMaxX > bMinX && aMinZ < bMaxZ && aMaxZ > bMinZ
}