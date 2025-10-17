package com.imyvm.iwg.util.geo

import net.minecraft.util.math.BlockPos

fun findNearestAdjacentPoints(
    blockPosList: List<BlockPos>,
    targetPoint: BlockPos
): Pair<BlockPos, BlockPos> {
    val n = blockPosList.size
    var minDistance = Double.MAX_VALUE
    var nearestEdgeIndex = 0

    for (i in blockPosList.indices) {
        val pointA = blockPosList[i]
        val pointB = blockPosList[(i + 1) % n]

        val distance = distanceToSegment(targetPoint, pointA, pointB)

        if (distance < minDistance) {
            minDistance = distance
            nearestEdgeIndex = i
        }
    }

    return Pair(blockPosList[nearestEdgeIndex], blockPosList[(nearestEdgeIndex + 1) % n])
}

fun distanceToSegment(point: BlockPos, segmentStart: BlockPos, segmentEnd: BlockPos): Double {
    val px = point.x.toDouble()
    val pz = point.z.toDouble()
    val ax = segmentStart.x.toDouble()
    val az = segmentStart.z.toDouble()
    val bx = segmentEnd.x.toDouble()
    val bz = segmentEnd.z.toDouble()

    val abx = bx - ax
    val abz = bz - az
    val apx = px - ax
    val apz = pz - az

    val abSquared = abx * abx + abz * abz
    if (abSquared == 0.0) {
        return kotlin.math.sqrt(apx * apx + apz * apz)
    }

    val t = (apx * abx + apz * abz) / abSquared
    val clampedT = t.coerceIn(0.0, 1.0)

    val closestX = ax + clampedT * abx
    val closestZ = az + clampedT * abz

    val dx = px - closestX
    val dz = pz - closestZ
    return kotlin.math.sqrt(dx * dx + dz * dz)
}