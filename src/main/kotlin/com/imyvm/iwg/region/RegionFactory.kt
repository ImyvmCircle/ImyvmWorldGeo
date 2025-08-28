import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.region.Region
import com.imyvm.iwg.util.checkIntersection
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.sqrt

sealed class CreationError {
    data object DuplicatedPoints : CreationError()
    data object InsufficientPoints : CreationError()
    data object CoincidentPoints : CreationError()
    data object UnderSizeLimit : CreationError()
    data object NotConvex : CreationError()
    data object IntersectionBetweenScopes : CreationError()
}

sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}

object RegionFactory {

    private const val MIN_RECTANGLE_AREA = 100.0
    private const val MIN_SIDE_LENGTH = 10.0
    private const val MIN_CIRCLE_RADIUS = 5.0
    private const val MIN_POLYGON_AREA = 100.0

    fun createRegion(
        name: String,
        numberID: Int,
        selectedPositions: MutableList<BlockPos>,
        shapeType: Region.Companion.GeoShapeType
    ): Result<Region, CreationError> {

        val requiredPoints = requiredPoints(shapeType)
        if (selectedPositions.size < requiredPoints) {
            return Result.Err(CreationError.InsufficientPoints)
        }

        val geoShapeResult = when (shapeType) {
            Region.Companion.GeoShapeType.RECTANGLE -> createRectangle(selectedPositions)
            Region.Companion.GeoShapeType.CIRCLE -> createCircle(selectedPositions)
            Region.Companion.GeoShapeType.POLYGON -> createPolygon(selectedPositions)
            else -> Result.Err(CreationError.InsufficientPoints)
        }

        if (geoShapeResult is Result.Err) {
            return geoShapeResult
        }

        val geoShape = (geoShapeResult as Result.Ok).value

        val existingScopes = ImyvmWorldGeo.data.getRegionList().flatMap { it.geometryScope }
        if (checkIntersection(geoShape, existingScopes)) {
            return Result.Err(CreationError.IntersectionBetweenScopes)
        }

        val geoScope = Region.Companion.GeoScope().apply {
            scopeName = "main_scope"
            this.geoShape = geoShape
        }

        val newRegion = Region().apply {
            this.name = name
            this.numberID = numberID
            geometryScope.add(geoScope)
        }

        return Result.Ok(newRegion)
    }

    private fun requiredPoints(shapeType: Region.Companion.GeoShapeType): Int =
        when (shapeType) {
            Region.Companion.GeoShapeType.CIRCLE,
            Region.Companion.GeoShapeType.RECTANGLE -> 2
            Region.Companion.GeoShapeType.POLYGON -> 3
            else -> 0
        }

    private fun createRectangle(positions: List<BlockPos>): Result<Region.Companion.GeoShape, CreationError> {
        val pos1 = positions[0]
        val pos2 = positions[1]

        if (pos1 == pos2) return Result.Err(CreationError.DuplicatedPoints)
        if (pos1.x == pos2.x || pos1.z == pos2.z) return Result.Err(CreationError.CoincidentPoints)

        val width = abs(pos1.x - pos2.x)
        val length = abs(pos1.z - pos2.z)

        if (!checkRectangleSize(width, length)) return Result.Err(CreationError.UnderSizeLimit)

        return Result.Ok(
            Region.Companion.GeoShape().apply {
                geoShapeType = Region.Companion.GeoShapeType.RECTANGLE
                shapeParameter = mutableListOf(
                    minOf(pos1.x, pos2.x), minOf(pos1.z, pos2.z),
                    maxOf(pos1.x, pos2.x), maxOf(pos1.z, pos2.z)
                )
            }
        )
    }

    private fun createCircle(positions: List<BlockPos>): Result<Region.Companion.GeoShape, CreationError> {
        val center = positions[0]
        val circumference = positions[1]

        if (center == circumference) return Result.Err(CreationError.DuplicatedPoints)

        val dx = circumference.x - center.x
        val dz = circumference.z - center.z
        val radius = sqrt((dx * dx + dz * dz).toDouble())

        if (!checkCircleSize(radius)) return Result.Err(CreationError.UnderSizeLimit)

        return Result.Ok(
            Region.Companion.GeoShape().apply {
                geoShapeType = Region.Companion.GeoShapeType.CIRCLE
                shapeParameter = mutableListOf(center.x, center.z, radius.toInt())
            }
        )
    }

    private fun createPolygon(positions: List<BlockPos>): Result<Region.Companion.GeoShape, CreationError> {
        val distinct = positions.distinct()
        if (distinct.size != positions.size) return Result.Err(CreationError.DuplicatedPoints)
        if (!isConvex(positions)) return Result.Err(CreationError.NotConvex)
        val area = polygonArea(positions)
        if (!checkPolygonSize(area)) return Result.Err(CreationError.UnderSizeLimit)

        return Result.Ok(
            Region.Companion.GeoShape().apply {
                geoShapeType = Region.Companion.GeoShapeType.POLYGON
                shapeParameter = positions.flatMap { listOf(it.x, it.z) }.toMutableList()
            }
        )
    }

    private fun checkRectangleSize(width: Int, length: Int): Boolean {
        val area = width * length
        return width >= MIN_SIDE_LENGTH && length >= MIN_SIDE_LENGTH && area >= MIN_RECTANGLE_AREA
    }

    private fun checkCircleSize(radius: Double) = radius >= MIN_CIRCLE_RADIUS
    private fun checkPolygonSize(area: Double) = area >= MIN_POLYGON_AREA

    private fun isConvex(positions: List<BlockPos>): Boolean {
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

    private fun polygonArea(positions: List<BlockPos>): Double {
        var area = 0.0
        val n = positions.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += (positions[i].x * positions[j].z - positions[j].x * positions[i].z)
        }
        return abs(area) / 2.0
    }
}
