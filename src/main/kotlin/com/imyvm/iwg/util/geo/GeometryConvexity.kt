package com.imyvm.iwg.util.geo

import net.minecraft.util.math.BlockPos

fun isConvex(positions: List<BlockPos>): Boolean {
    if (positions.size < 3) return false
    var sign = 0
    val n = positions.size
    for (i in 0 until n) {
        val p1 = positions[i]
        val p2 = positions[(i + 1) % n]
        val p3 = positions[(i + 2) % n]
        val cross = (p2.x - p1.x) * (p3.z - p2.z) - (p2.z - p1.z) * (p3.x - p2.x)
        if (cross != 0) {
            val currentSign = if (cross > 0) 1 else -1
            if (sign == 0) sign = currentSign
            else if (sign != currentSign) return false
        }
    }
    return true
}