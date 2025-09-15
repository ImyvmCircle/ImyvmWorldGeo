package com.imyvm.iwg.util.geo

fun isInsideCircle(x: Int, y: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 3) return false
    val centerX = shapeParameter[0]
    val centerY = shapeParameter[1]
    val radius = shapeParameter[2]
    val dx = x - centerX
    val dy = y - centerY
    return dx * dx + dy * dy <= radius * radius
}

fun isInsideRectangle(x: Int, y: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 4) return false
    val west = shapeParameter[0]
    val north = shapeParameter[1]
    val east = shapeParameter[2]
    val south = shapeParameter[3]
    return x in west..east && y in north..south
}

fun isInsidePolygon(x: Int, y: Int, shapeParameter: List<Int>): Boolean {
    if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) return false
    return polygonContainsPoint(
        x, y,
        shapeParameter.chunked(2).map { Pair(it[0], it[1]) }
    )
}

private fun polygonContainsPoint(x: Int, y: Int, vertices: List<Pair<Int, Int>>): Boolean {
    var inside = false
    var j = vertices.size - 1

    for (i in vertices.indices) {
        val xi = vertices[i].first
        val yi = vertices[i].second
        val xj = vertices[j].first
        val yj = vertices[j].second

        val cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
        if (cross == 0) {
            if (x in minOf(xi, xj)..maxOf(xi, xj) &&
                y in minOf(yi, yj)..maxOf(yi, yj)
            ) {
                return true
            }
        }

        val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi).toDouble() / (yj - yi) + xi)
        if (intersect) inside = !inside
        j = i
    }

    return inside
}