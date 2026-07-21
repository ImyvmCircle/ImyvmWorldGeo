package com.imyvm.iwg.util.geo
import com.imyvm.iwg.infra.config.GeoConfig.MIN_CIRCLE_RADIUS
import com.imyvm.iwg.infra.config.GeoConfig.MIN_POLYGON_AREA
import com.imyvm.iwg.infra.config.GeoConfig.MIN_RECTANGLE_AREA
import com.imyvm.iwg.infra.config.GeoConfig.MIN_SIDE_LENGTH
import com.imyvm.iwg.infra.config.GeoConfig.MIN_POLYGON_SPAN
import com.imyvm.iwg.infra.config.GeoConfig.MIN_ASPECT_RATIO
import com.imyvm.iwg.infra.config.GeoConfig.MIN_EDGE_LENGTH
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import net.minecraft.core.BlockPos
import java.math.BigInteger

internal data class GeometrySizeLimits(
    val minRectangleArea: Double,
    val minSideLength: Double,
    val minCircleRadius: Double,
    val minPolygonArea: Double,
    val minPolygonSpan: Double,
    val minAspectRatio: Double,
    val minEdgeLength: Double
)

internal val regionGeometrySizeLimits: GeometrySizeLimits
    get() = GeometrySizeLimits(
        MIN_RECTANGLE_AREA.value,
        MIN_SIDE_LENGTH.value,
        MIN_CIRCLE_RADIUS.value,
        MIN_POLYGON_AREA.value,
        MIN_POLYGON_SPAN.value,
        MIN_ASPECT_RATIO.value,
        MIN_EDGE_LENGTH.value
    )

internal val subSpaceGeometrySizeLimits = GeometrySizeLimits(
    minRectangleArea = 450.0,
    minSideLength = 15.0,
    minCircleRadius = 12.0,
    minPolygonArea = 450.0,
    minPolygonSpan = 15.0,
    minAspectRatio = 0.2,
    minEdgeLength = 6.0
)

fun checkRectangleSize(width: Int, length: Int): CreationError? =
    checkRectangleSize(width.toLong(), length.toLong())

fun checkRectangleSize(width: Long, length: Long): CreationError? =
    checkRectangleSize(width, length, regionGeometrySizeLimits)

internal fun checkRectangleSize(width: Long, length: Long, limits: GeometrySizeLimits): CreationError? {
    val area = width.toDouble() * length
    if (area < limits.minRectangleArea) return CreationError.UnderSizeLimit
    if (width < limits.minSideLength || length < limits.minSideLength) return CreationError.EdgeTooShort

    val aspectRatio = if (length == 0L) Double.MAX_VALUE else width.toDouble() / length
    if (aspectRatio < limits.minAspectRatio || aspectRatio > (1.0 / limits.minAspectRatio)) {
        return CreationError.AspectRatioInvalid
    }

    return null
}

fun checkCircleSize(radius: Double) = radius >= MIN_CIRCLE_RADIUS.value

internal fun checkCircleSize(radius: Double, limits: GeometrySizeLimits) = radius >= limits.minCircleRadius

fun checkPolygonSize(positions: List<BlockPos>): CreationError? =
    checkPolygonSize(positions, regionGeometrySizeLimits)

internal fun checkPolygonSize(positions: List<BlockPos>, limits: GeometrySizeLimits): CreationError? {
    if (!isPolygonVertexCountSupported(positions.size)) return CreationError.PolygonVertexLimitExceeded
    val xs = positions.map { it.x }
    val zs = positions.map { it.z }

    return checkArea(polygonTwiceArea(positions.size, { positions[it].x }, { positions[it].z }), limits)
        ?: checkBoundingBox(xs, zs, limits)
        ?: checkAspectRatio(xs, zs, limits)
        ?: checkEdges(positions, limits)
}

fun calculateCircleArea(shapeParameter : MutableList<Int>) : Double {
    return if (shapeParameter.size < 3) 0.0
    else {
        val radius = shapeParameter[2].toDouble()
        Math.PI * radius * radius
    }
}

fun calculateRectangleArea(shapeParameter: MutableList<Int>) : Double {
    return if (shapeParameter.size < 4) 0.0
    else {
        val west = shapeParameter[0].toDouble()
        val north = shapeParameter[1].toDouble()
        val east = shapeParameter[2].toDouble()
        val south = shapeParameter[3].toDouble()
        (east - west) * (south - north)
    }
}

@JvmName("calculatePolygonAreaInt")
fun calculatePolygonArea(shapeParameter: List<Int>): Double {
    if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) return 0.0
    return polygonTwiceArea(
        shapeParameter.size / 2,
        { shapeParameter[it * 2] },
        { shapeParameter[it * 2 + 1] }
    ).toDouble() / 2.0
}

fun calculatePolygonArea(positions: List<BlockPos>): Double {
    if (positions.size < 3) return 0.0
    return polygonTwiceArea(positions.size, { positions[it].x }, { positions[it].z }).toDouble() / 2.0
}

fun getBoundingBox(shapeParameters: MutableList<Int>): IntArray {
    val minX = shapeParameters.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0
    val minZ = shapeParameters.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0
    val maxX = shapeParameters.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: 0
    val maxZ = shapeParameters.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: 0
    return intArrayOf(minX, minZ, maxX, maxZ)
}

private fun checkArea(twiceArea: BigInteger, limits: GeometrySizeLimits): CreationError? {
    return if (twiceArea.toDouble() < limits.minPolygonArea * 2.0) CreationError.UnderSizeLimit else null
}

private fun checkBoundingBox(xs: List<Int>, zs: List<Int>, limits: GeometrySizeLimits): CreationError? {
    val width = xs.maxOrNull()!!.toLong() - xs.minOrNull()!!
    val height = zs.maxOrNull()!!.toLong() - zs.minOrNull()!!
    return if (width < limits.minPolygonSpan || height < limits.minPolygonSpan) {
        CreationError.UnderBoundingBoxLimit
    } else null
}

private fun checkAspectRatio(xs: List<Int>, zs: List<Int>, limits: GeometrySizeLimits): CreationError? {
    val width = xs.maxOrNull()!!.toLong() - xs.minOrNull()!!
    val height = zs.maxOrNull()!!.toLong() - zs.minOrNull()!!
    val aspectRatio = if (height == 0L) Double.MAX_VALUE else width.toDouble() / height
    return if (aspectRatio < limits.minAspectRatio || aspectRatio > (1.0 / limits.minAspectRatio)) {
        CreationError.AspectRatioInvalid
    } else null
}

private fun checkEdges(positions: List<BlockPos>, limits: GeometrySizeLimits): CreationError? {
    val n = positions.size
    for (i in positions.indices) {
        val p1 = positions[i]
        val p2 = positions[(i + 1) % n]
        val dx = p1.x.toDouble() - p2.x
        val dz = p1.z.toDouble() - p2.z
        val length = kotlin.math.hypot(dx, dz)
        if (length < limits.minEdgeLength) {
            return CreationError.EdgeTooShort
        }
    }
    return null
}

internal inline fun polygonTwiceArea(
    vertexCount: Int,
    xAt: (Int) -> Int,
    zAt: (Int) -> Int
): BigInteger {
    var twiceArea = BigInteger.ZERO
    for (index in 0 until vertexCount) {
        val next = (index + 1) % vertexCount
        val forward = BigInteger.valueOf(xAt(index).toLong()).multiply(BigInteger.valueOf(zAt(next).toLong()))
        val backward = BigInteger.valueOf(xAt(next).toLong()).multiply(BigInteger.valueOf(zAt(index).toLong()))
        twiceArea = twiceArea.add(forward.subtract(backward))
    }
    return twiceArea.abs()
}
