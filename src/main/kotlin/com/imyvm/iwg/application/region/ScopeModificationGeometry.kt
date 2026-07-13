package com.imyvm.iwg.application.region

import net.minecraft.core.BlockPos
import kotlin.math.abs

internal fun updateRectangleBounds(point: BlockPos, shapeParams: List<Int>): IntArray {
    require(shapeParams.size == 4) { "rectangle requires west/north/east/south" }
    var west = shapeParams[0]
    var north = shapeParams[1]
    var east = shapeParams[2]
    var south = shapeParams[3]

    if (abs(point.x.toLong() - west) < abs(point.x.toLong() - east)) west = point.x else east = point.x
    if (abs(point.z.toLong() - north) < abs(point.z.toLong() - south)) north = point.z else south = point.z
    return intArrayOf(west, north, east, south)
}
