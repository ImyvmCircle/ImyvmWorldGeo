package com.imyvm.iwg.util.geo

import java.util.PriorityQueue

private data class GridPoint(val x: Int, val z: Int, val distanceSquared: Double)

fun iterateCirclePoint(centerX: Int, centerZ: Int, radius: Int): Sequence<Pair<Int, Int>> {
    require(radius >= 0) { "circle radius must not be negative" }
    val minX = Math.subtractExact(centerX, radius)
    val maxX = Math.addExact(centerX, radius)
    val minZ = Math.subtractExact(centerZ, radius)
    val maxZ = Math.addExact(centerZ, radius)
    return generateShapePoints(minX, maxX, minZ, maxZ) { x, z ->
        circleContainsPoint(x.toLong() - centerX, z.toLong() - centerZ, radius)
    }
}

fun iterateRectanglePoint(x1: Int, z1: Int, x2: Int, z2: Int): Sequence<Pair<Int, Int>> =
    generateShapePoints(minOf(x1, x2), maxOf(x1, x2), minOf(z1, z2), maxOf(z1, z2)) { _, _ -> true }

fun iteratePolygonPoint(shapeParameters: MutableList<Int>): Sequence<Pair<Int, Int>> {
    require(shapeParameters.size >= 6 && shapeParameters.size % 2 == 0) {
        "polygon requires at least three coordinate pairs"
    }
    val (minX, minZ, maxX, maxZ) = getBoundingBox(shapeParameters)
    return generateShapePoints(minX, maxX, minZ, maxZ) { x, z ->
        polygonContainsPoint(x, z, shapeParameters)
    }
}

internal fun generateShapePoints(
    minX: Int,
    maxX: Int,
    minZ: Int,
    maxZ: Int,
    containsPoint: (Int, Int) -> Boolean
): Sequence<Pair<Int, Int>> {
    require(minX <= maxX && minZ <= maxZ) { "invalid shape bounds" }
    val centerX = ((minX.toLong() + maxX.toLong()) / 2L).toInt()
    val centerZ = ((minZ.toLong() + maxZ.toLong()) / 2L).toInt()
    return distanceOrderedGrid(centerX, centerZ, minX, maxX, minZ, maxZ)
        .filter { (x, z) -> containsPoint(x, z) }
}

internal fun distanceOrderedGrid(
    centerX: Int,
    centerZ: Int,
    minX: Int,
    maxX: Int,
    minZ: Int,
    maxZ: Int
): Sequence<Pair<Int, Int>> = sequence {
    if (centerX !in minX..maxX || centerZ !in minZ..maxZ) return@sequence
    val queue = PriorityQueue(compareBy<GridPoint> { it.distanceSquared }.thenBy { it.x }.thenBy { it.z })
    val visited = mutableSetOf<Long>()

    fun enqueue(x: Long, z: Long) {
        if (x !in minX.toLong()..maxX.toLong() || z !in minZ.toLong()..maxZ.toLong()) return
        val intX = x.toInt()
        val intZ = z.toInt()
        val packed = (intX.toLong() shl 32) xor (intZ.toLong() and 0xffffffffL)
        if (!visited.add(packed)) return
        val dx = x.toDouble() - centerX
        val dz = z.toDouble() - centerZ
        queue += GridPoint(intX, intZ, dx * dx + dz * dz)
    }

    enqueue(centerX.toLong(), centerZ.toLong())
    while (queue.isNotEmpty()) {
        val point = queue.remove()
        yield(point.x to point.z)
        enqueue(point.x.toLong() - 1L, point.z.toLong())
        enqueue(point.x.toLong() + 1L, point.z.toLong())
        enqueue(point.x.toLong(), point.z.toLong() - 1L)
        enqueue(point.x.toLong(), point.z.toLong() + 1L)
    }
}
