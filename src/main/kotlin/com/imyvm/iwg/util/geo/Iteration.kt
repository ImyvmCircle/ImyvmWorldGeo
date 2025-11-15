package com.imyvm.iwg.util.geo

fun iterateCirclePoint(centerX: Int, centerZ: Int, radius: Int): List<Pair<Int, Int>> {
    return generateShapePoints(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius) { x, z ->
        circleContainsPoint(x - centerX, z - centerZ, radius)
    }
}

fun iterateRectanglePoint(x1: Int, z1: Int, x2: Int, z2: Int): List<Pair<Int, Int>> {
    return generateShapePoints(x1, x2, z1, z2) { x, z ->
        rectangleContainsPoint(x, z, x1, x2, z1, z2)
    }
}

fun iteratePolygonPoint(shapeParameters: MutableList<Int>): List<Pair<Int, Int>> {
    val (minX, minZ, maxX, maxZ) = getBoundingBox(shapeParameters)
    val vertices = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until shapeParameters.size step 2) {
        vertices.add(Pair(shapeParameters[i], shapeParameters[i + 1]))
    }

    return generateShapePoints(minX, maxX, minZ, maxZ) { x, z ->
        polygonContainsPoint(x, z, vertices)
    }
}

fun generateShapePoints(minX: Int, maxX: Int, minZ: Int, maxZ: Int, containsPoint: (Int, Int) -> Boolean): List<Pair<Int, Int>> {
    val pointList = mutableListOf<Pair<Int, Int>>()
    for (x in minX..maxX) {
        for (z in minZ..maxZ) {
            if (containsPoint(x, z)) {
                pointList.add(Pair(x, z))
            }
        }
    }

    val centerX = (minX + maxX) / 2
    val centerZ = (minZ + maxZ) / 2
    return pointList.sortedBy { (x, z) ->
        (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ)
    }
}