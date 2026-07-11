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
    return polygonContainsPoint(
        x, z,
        shapeParameter.chunked(2).map { Pair(it[0], it[1]) }
    )
}

@JvmName("circlePointContainment")
fun circleContainsPoint(dx: Int, dy: Int, radius: Int): Boolean {
    require(radius >= 0) { "circle radius must not be negative" }
    val longDx = dx.toLong()
    val longDy = dy.toLong()
    val longRadius = radius.toLong()
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
    var inside = false
    var j = vertices.size - 1

    for (i in vertices.indices) {
        val xi = vertices[i].first
        val zi = vertices[i].second
        val xj = vertices[j].first
        val zj = vertices[j].second

        val cross = (x.toDouble() - xi) * (zj.toDouble() - zi) -
            (z.toDouble() - zi) * (xj.toDouble() - xi)
        if (cross == 0.0) {
            if (x in minOf(xi, xj)..maxOf(xi, xj) &&
                z in minOf(zi, zj)..maxOf(zi, zj)
            ) {
                return true
            }
        }

        val intersect = ((zi > z) != (zj > z)) &&
                (x < (xj - xi) * (z - zi).toDouble() / (zj - zi) + xi)
        if (intersect) inside = !inside
        j = i
    }

    return inside
}
