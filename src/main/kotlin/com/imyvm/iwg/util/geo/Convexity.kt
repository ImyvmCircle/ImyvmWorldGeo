package com.imyvm.iwg.util.geo

import net.minecraft.core.BlockPos
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.atan2

fun isConvex(positions: List<BlockPos>): Boolean {
    return isConvexCoordinates(positions.size, { positions[it].x }, { positions[it].z })
}

internal inline fun isConvexCoordinates(
    vertexCount: Int,
    xAt: (Int) -> Int,
    zAt: (Int) -> Int
): Boolean {
    if (vertexCount < 3) return false
    var sign = 0
    var totalTurn = 0.0
    for (index in 0 until vertexCount) {
        val second = (index + 1) % vertexCount
        val third = (index + 2) % vertexCount
        val firstEdgeX = xAt(second).toLong() - xAt(index)
        val firstEdgeZ = zAt(second).toLong() - zAt(index)
        val secondEdgeX = xAt(third).toLong() - xAt(second)
        val secondEdgeZ = zAt(third).toLong() - zAt(second)
        val cross = exactConvexityCross(firstEdgeX, firstEdgeZ, secondEdgeX, secondEdgeZ)
        val currentSign = cross.signum()
        val dot = firstEdgeX.toDouble() * secondEdgeX + firstEdgeZ.toDouble() * secondEdgeZ
        if (currentSign == 0) {
            if (dot <= 0.0) return false
            continue
        }
        if (sign == 0) sign = currentSign else if (sign != currentSign) return false
        totalTurn += atan2(cross.toDouble(), dot)
    }
    return sign != 0 && abs(abs(totalTurn) - Math.PI * 2.0) < 1e-7
}

@PublishedApi
internal fun exactConvexityCross(ax: Long, az: Long, bx: Long, bz: Long): BigInteger =
    BigInteger.valueOf(ax).multiply(BigInteger.valueOf(bz))
        .subtract(BigInteger.valueOf(az).multiply(BigInteger.valueOf(bx)))
