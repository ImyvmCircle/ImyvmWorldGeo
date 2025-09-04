package com.imyvm.iwg.util.geo
import com.imyvm.iwg.ModConfig.Companion.MIN_CIRCLE_RADIUS
import com.imyvm.iwg.ModConfig.Companion.MIN_POLYGON_AREA
import com.imyvm.iwg.ModConfig.Companion.MIN_RECTANGLE_AREA
import com.imyvm.iwg.ModConfig.Companion.MIN_SIDE_LENGTH
import com.imyvm.iwg.ModConfig.Companion.MIN_POLYGON_SPAN
import com.imyvm.iwg.ModConfig.Companion.MIN_ASPECT_RATIO
import com.imyvm.iwg.ModConfig.Companion.MIN_EDGE_LENGTH
import com.imyvm.iwg.domain.CreationError
import net.minecraft.util.math.BlockPos

fun checkRectangleSize(width: Int, length: Int): CreationError? {
    val area = width * length

    if (area < MIN_RECTANGLE_AREA.value) {
        return CreationError.UnderSizeLimit
    }

    if (width < MIN_SIDE_LENGTH.value || length < MIN_SIDE_LENGTH.value) {
        return CreationError.EdgeTooShort
    }

    val aspectRatio = if (length == 0) Double.MAX_VALUE else width.toDouble() / length
    if (aspectRatio < MIN_ASPECT_RATIO.value|| aspectRatio > (1.0 / MIN_ASPECT_RATIO.value)) {
        return CreationError.AspectRatioInvalid
    }

    return null
}

fun checkCircleSize(radius: Double) = radius >= MIN_CIRCLE_RADIUS.value

fun checkPolygonSize(positions: List<BlockPos>, area: Double): CreationError? {
    val xs = positions.map { it.x }
    val zs = positions.map { it.z }

    return checkArea(area)
        ?: checkBoundingBox(xs, zs)
        ?: checkAspectRatio(xs, zs)
        ?: checkEdges(positions)
}

private fun checkArea(area: Double): CreationError? {
    return if (area < MIN_POLYGON_AREA.value) CreationError.UnderSizeLimit else null
}

private fun checkBoundingBox(xs: List<Int>, zs: List<Int>): CreationError? {
    val width = (xs.maxOrNull()!! - xs.minOrNull()!!)
    val height = (zs.maxOrNull()!! - zs.minOrNull()!!)
    return if (width < MIN_POLYGON_SPAN.value || height < MIN_POLYGON_SPAN.value) {
        CreationError.UnderBoundingBoxLimit
    } else null
}

private fun checkAspectRatio(xs: List<Int>, zs: List<Int>): CreationError? {
    val width = (xs.maxOrNull()!! - xs.minOrNull()!!).toDouble()
    val height = (zs.maxOrNull()!! - zs.minOrNull()!!).toDouble()
    val aspectRatio = if (height == 0.0) Double.MAX_VALUE else width / height
    return if (aspectRatio < MIN_ASPECT_RATIO.value|| aspectRatio > (1.0 / MIN_ASPECT_RATIO.value)) {
        CreationError.AspectRatioInvalid
    } else null
}

private fun checkEdges(positions: List<BlockPos>): CreationError? {
    val n = positions.size
    for (i in positions.indices) {
        val p1 = positions[i]
        val p2 = positions[(i + 1) % n]
        val dx = (p1.x - p2.x).toDouble()
        val dz = (p1.z - p2.z).toDouble()
        val length = kotlin.math.sqrt(dx * dx + dz * dz)
        if (length < MIN_EDGE_LENGTH.value) {
            return CreationError.EdgeTooShort
        }
    }
    return null
}