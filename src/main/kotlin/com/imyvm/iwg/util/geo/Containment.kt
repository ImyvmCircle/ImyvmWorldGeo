package com.imyvm.iwg.util.geo

fun circleContainsPoint(x: Int, y: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 3) return false
    val centerX = shapeParameter[0]
    val centerY = shapeParameter[1]
    val radius = shapeParameter[2]
    val dx = x.toLong() - centerX
    val dy = y.toLong() - centerY
    return circleContainsPoint(dx, dy, radius)
}

fun rectangleContainsPoint(x: Int, z: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 4) return false
    val west = shapeParameter[0]
    val north = shapeParameter[1]
    val east = shapeParameter[2]
    val south = shapeParameter[3]
    return rectangleContainsPoint(x, z, west, east, north, south)
}

fun polygonContainsPoint(x: Int, z: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) return false
    return polygonContainsPointCoordinates(
        shapeParameter.size / 2,
        { shapeParameter[it * 2] },
        { shapeParameter[it * 2 + 1] },
        x,
        z
    )
}

@JvmName("circlePointContainment")
fun circleContainsPoint(dx: Int, dy: Int, radius: Int): Boolean {
    require(radius >= 0) { "circle radius must not be negative" }
    val longDx = dx.toLong()
    val longDy = dy.toLong()
    val longRadius = radius.toLong()
    if (kotlin.math.abs(longDx) > longRadius || kotlin.math.abs(longDy) > longRadius) return false
    return longDx * longDx + longDy * longDy <= longRadius * longRadius
}

internal fun circleContainsPoint(dx: Long, dy: Long, radius: Int): Boolean {
    require(radius >= 0) { "circle radius must not be negative" }
    val longRadius = radius.toLong()
    if (kotlin.math.abs(dx) > longRadius || kotlin.math.abs(dy) > longRadius) return false
    return dx * dx + dy * dy <= longRadius * longRadius
}

@JvmName("rectanglePointContainment")
fun rectangleContainsPoint(x: Int, z: Int, west: Int, east: Int, north: Int, south: Int): Boolean {
    return x in west..east && z in north..south
}

@JvmName("polygonPointContainment")
fun polygonContainsPoint(x: Int, z: Int, vertices: List<Pair<Int, Int>>): Boolean {
    return polygonContainsPointCoordinates(
        vertices.size,
        { vertices[it].first },
        { vertices[it].second },
        x,
        z
    )
}

internal inline fun polygonContainsPointCoordinates(
    vertexCount: Int,
    xAt: (Int) -> Int,
    zAt: (Int) -> Int,
    x: Int,
    z: Int
): Boolean {
    var winding = 0
    var previous = vertexCount - 1
    for (current in 0 until vertexCount) {
        val currentX = xAt(current)
        val currentZ = zAt(current)
        val previousX = xAt(previous)
        val previousZ = zAt(previous)
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
