package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.GeoScope
import com.imyvm.iwg.domain.GeoShape
import com.imyvm.iwg.domain.GeoShapeType
import net.minecraft.util.math.BlockPos

fun checkIntersection(
    newShape: GeoShape,
    existingScopes: List<GeoScope>
): Boolean {
    for (scope in existingScopes) {
        val existingGeo = scope.geoShape ?: continue
        if (geoOverlap(newShape, existingGeo)) return true
    }
    return false
}

private fun geoOverlap(a: GeoShape, b: GeoShape): Boolean {
    return when (a.geoShapeType) {
        GeoShapeType.RECTANGLE -> when (b.geoShapeType) {
            GeoShapeType.RECTANGLE -> rectOverlapRect(a, b)
            GeoShapeType.CIRCLE -> rectOverlapCircle(a, b)
            GeoShapeType.POLYGON -> rectOverlapPolygon(a, b)
            else -> false
        }
        GeoShapeType.CIRCLE -> when (b.geoShapeType) {
            GeoShapeType.RECTANGLE -> rectOverlapCircle(b, a)
            GeoShapeType.CIRCLE -> circleOverlapCircle(a, b)
            GeoShapeType.POLYGON -> polygonOverlapCircle(b, a)
            else -> false
        }
        GeoShapeType.POLYGON -> when (b.geoShapeType) {
            GeoShapeType.RECTANGLE -> rectOverlapPolygon(b, a)
            GeoShapeType.CIRCLE -> polygonOverlapCircle(a, b)
            GeoShapeType.POLYGON -> polygonOverlapPolygon(a, b)
            else -> false
        }
        else -> false
    }
}

private fun rectOverlapRect(a: GeoShape, b: GeoShape): Boolean {
    val ax1 = a.shapeParameter[0]
    val az1 = a.shapeParameter[1]
    val ax2 = a.shapeParameter[2]
    val az2 = a.shapeParameter[3]

    val bx1 = b.shapeParameter[0]
    val bz1 = b.shapeParameter[1]
    val bx2 = b.shapeParameter[2]
    val bz2 = b.shapeParameter[3]

    return ax1 <= bx2 && ax2 >= bx1 && az1 <= bz2 && az2 >= bz1
}

private fun rectOverlapCircle(rect: GeoShape, circle: GeoShape): Boolean {
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

private fun circleOverlapCircle(a: GeoShape, b: GeoShape): Boolean {
    val dx = a.shapeParameter[0] - b.shapeParameter[0]
    val dz = a.shapeParameter[1] - b.shapeParameter[1]
    val rSum = a.shapeParameter[2] + b.shapeParameter[2]
    return dx * dx + dz * dz <= rSum * rSum
}

private fun rectOverlapPolygon(rect:GeoShape, poly: GeoShape): Boolean {
    val rectPoly = listOf(
        BlockPos(rect.shapeParameter[0], 0, rect.shapeParameter[1]),
        BlockPos(rect.shapeParameter[2], 0, rect.shapeParameter[1]),
        BlockPos(rect.shapeParameter[2], 0, rect.shapeParameter[3]),
        BlockPos(rect.shapeParameter[0], 0, rect.shapeParameter[3])
    )
    return polygonsIntersect(rectPoly, poly.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) })
}

private fun polygonOverlapPolygon(a: GeoShape, b: GeoShape): Boolean {
    return polygonsIntersect(
        a.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) },
        b.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) }
    )
}

private fun polygonOverlapCircle(poly: GeoShape, circle: GeoShape): Boolean {
    val cx = circle.shapeParameter[0]
    val cz = circle.shapeParameter[1]
    val r = circle.shapeParameter[2]

    val polyPoints = poly.shapeParameter.chunked(2).map { BlockPos(it[0], 0, it[1]) }

    if (polyPoints.any { (it.x - cx) * (it.x - cx) + (it.z - cz) * (it.z - cz) <= r * r }) {
        return true
    }

    if (pointInPolygon(BlockPos(cx, 0, cz), polyPoints)) {
        return true
    }

    for (i in polyPoints.indices) {
        val p1 = polyPoints[i]
        val p2 = polyPoints[(i + 1) % polyPoints.size]
        if (segmentCircleIntersect(p1, p2, cx, cz, r)) {
            return true
        }
    }

    return false
}

private fun segmentCircleIntersect(p1: BlockPos, p2: BlockPos, cx: Int, cz: Int, r: Int): Boolean {
    val dx = (p2.x - p1.x).toDouble()
    val dz = (p2.z - p1.z).toDouble()

    val fx = (p1.x - cx).toDouble()
    val fz = (p1.z - cz).toDouble()

    val a = dx * dx + dz * dz
    val b = 2 * (fx * dx + fz * dz)
    val c = fx * fx + fz * fz - r * r

    val discriminant = b * b - 4 * a * c
    if (discriminant < 0) return false

    val sqrtDisc = kotlin.math.sqrt(discriminant)
    val t1 = (-b - sqrtDisc) / (2 * a)
    val t2 = (-b + sqrtDisc) / (2 * a)

    return (t1 in 0.0..1.0) || (t2 in 0.0..1.0)
}


private fun polygonsIntersect(a: List<BlockPos>, b: List<BlockPos>): Boolean {
    for (i in a.indices) {
        val a1 = a[i]
        val a2 = a[(i + 1) % a.size]
        for (j in b.indices) {
            val b1 = b[j]
            val b2 = b[(j + 1) % b.size]
            if (segmentsIntersect(a1, a2, b1, b2)) return true
        }
    }

    if (pointInPolygon(a[0], b)) return true
    if (pointInPolygon(b[0], a)) return true

    return false
}

private fun segmentsIntersect(p1: BlockPos, p2: BlockPos, q1: BlockPos, q2: BlockPos): Boolean {
    fun orientation(a: BlockPos, b: BlockPos, c: BlockPos): Int {
        val value = (b.z - a.z) * (c.x - b.x) - (b.x - a.x) * (c.z - b.z)
        return when {
            value == 0 -> 0
            value > 0 -> 1
            else -> 2
        }
    }

    fun onSegment(a: BlockPos, b: BlockPos, c: BlockPos): Boolean {
        return b.x in minOf(a.x, c.x)..maxOf(a.x, c.x) &&
                b.z in minOf(a.z, c.z)..maxOf(a.z, c.z)
    }

    val o1 = orientation(p1, p2, q1)
    val o2 = orientation(p1, p2, q2)
    val o3 = orientation(q1, q2, p1)
    val o4 = orientation(q1, q2, p2)

    if (o1 != o2 && o3 != o4) return true

    if (o1 == 0 && onSegment(p1, q1, p2)) return true
    if (o2 == 0 && onSegment(p1, q2, p2)) return true
    if (o3 == 0 && onSegment(q1, p1, q2)) return true
    if (o4 == 0 && onSegment(q1, p2, q2)) return true

    return false
}


private fun pointInPolygon(p: BlockPos, polygon: List<BlockPos>): Boolean {
    var count = 0
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.z > p.z) != (pj.z > p.z) &&
            p.x < (pj.x - pi.x) * (p.z - pi.z).toDouble() / (pj.z - pi.z).toDouble() + pi.x
        ) {
            count++
        }
        j = i
    }
    return count % 2 == 1
}
