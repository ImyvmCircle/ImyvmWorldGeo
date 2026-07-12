package com.imyvm.iwg.util.geo

import java.util.PriorityQueue

private data class GridPoint(val x: Int, val z: Int, val distanceSquared: ExactSquaredDistance)

internal data class ExactSquaredDistance(val carry: Boolean, val low: ULong) : Comparable<ExactSquaredDistance> {
    override fun compareTo(other: ExactSquaredDistance): Int =
        compareValuesBy(this, other, ExactSquaredDistance::carry, ExactSquaredDistance::low)
}

internal fun exactSquaredDistance(x: Long, z: Long, centerX: Int, centerZ: Int): ExactSquaredDistance {
    val dx = kotlin.math.abs(x - centerX.toLong()).toULong()
    val dz = kotlin.math.abs(z - centerZ.toLong()).toULong()
    val xSquared = dx * dx
    val zSquared = dz * dz
    val low = xSquared + zSquared
    return ExactSquaredDistance(low < xSquared, low)
}

fun iterateCirclePointSequence(centerX: Int, centerZ: Int, radius: Int): Sequence<Pair<Int, Int>> {
    require(radius >= 0) { "circle radius must not be negative" }
    val minX = Math.subtractExact(centerX, radius)
    val maxX = Math.addExact(centerX, radius)
    val minZ = Math.subtractExact(centerZ, radius)
    val maxZ = Math.addExact(centerZ, radius)
    return generateShapePointSequence(minX, maxX, minZ, maxZ) { x, z ->
        circleContainsPoint(x.toLong() - centerX, z.toLong() - centerZ, radius)
    }
}

fun iterateRectanglePointSequence(x1: Int, z1: Int, x2: Int, z2: Int): Sequence<Pair<Int, Int>> =
    generateShapePointSequence(minOf(x1, x2), maxOf(x1, x2), minOf(z1, z2), maxOf(z1, z2)) { _, _ -> true }

fun iteratePolygonPointSequence(shapeParameters: MutableList<Int>): Sequence<Pair<Int, Int>> {
    require(shapeParameters.size >= 6 && shapeParameters.size % 2 == 0) {
        "polygon requires at least three coordinate pairs"
    }
    val (minX, minZ, maxX, maxZ) = getBoundingBox(shapeParameters)
    return generateShapePointSequence(minX, maxX, minZ, maxZ) { x, z ->
        polygonContainsPoint(x, z, shapeParameters)
    }
}

fun generateShapePointSequence(
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

@Deprecated("Materializes the whole shape; use iterateCirclePointSequence")
fun iterateCirclePoint(centerX: Int, centerZ: Int, radius: Int): List<Pair<Int, Int>> =
    iterateCirclePointSequence(centerX, centerZ, radius).toList()

@Deprecated("Materializes the whole shape; use iterateRectanglePointSequence")
fun iterateRectanglePoint(x1: Int, z1: Int, x2: Int, z2: Int): List<Pair<Int, Int>> =
    iterateRectanglePointSequence(x1, z1, x2, z2).toList()

@Deprecated("Materializes the whole shape; use iteratePolygonPointSequence")
fun iteratePolygonPoint(shapeParameters: MutableList<Int>): List<Pair<Int, Int>> =
    iteratePolygonPointSequence(shapeParameters).toList()

@Deprecated("Materializes the whole shape; use generateShapePointSequence")
fun generateShapePoints(
    minX: Int,
    maxX: Int,
    minZ: Int,
    maxZ: Int,
    containsPoint: (Int, Int) -> Boolean
): List<Pair<Int, Int>> = generateShapePointSequence(minX, maxX, minZ, maxZ, containsPoint).toList()

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
        queue += GridPoint(intX, intZ, exactSquaredDistance(x, z, centerX, centerZ))
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
