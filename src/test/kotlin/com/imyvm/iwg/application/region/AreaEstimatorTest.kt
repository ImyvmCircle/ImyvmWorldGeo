package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AreaEstimatorTest {
    @Test
    fun `circle estimation area equals stored shape area for fractional radius`() {
        val center = BlockPos(0, 0, 0)
        val circumference = BlockPos(45, 0, 7)
        val positions = listOf(center, circumference)

        val estimateResult = AreaEstimator.estimateShapeArea(positions, GeoShapeType.CIRCLE)
        val createResult = RegionFactory.createCircle(positions)

        val estimatedArea = (estimateResult as AreaEstimationResult.Success).area
        val storedArea = (createResult as Result.Ok).value.calculateArea()
        assertEquals(storedArea, estimatedArea)
    }

    @Test
    fun `circle creation reports unsupported int range`() {
        val positions = listOf(
            BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE),
            BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)
        )
        val result = RegionFactory.createCircle(
            positions
        )

        assertTrue(result is Result.Err && result.error == CreationError.CoordinateRangeExceeded)
        assertEquals(
            AreaEstimationResult.Error(CreationError.CoordinateRangeExceeded),
            AreaEstimator.estimateShapeArea(positions, GeoShapeType.CIRCLE)
        )
    }

    @Test
    fun `circle creation reports center radius extent overflow`() {
        val result = RegionFactory.createCircle(
            listOf(
                BlockPos(Int.MAX_VALUE - 10, 0, 0),
                BlockPos(Int.MAX_VALUE - 110, 0, 0)
            )
        )

        assertEquals(Result.Err(CreationError.CoordinateRangeExceeded), result)
    }

    @Test
    fun `rectangle area handles int boundary coordinates`() {
        val result = AreaEstimator.estimateShapeArea(
            listOf(BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE), BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)),
            GeoShapeType.RECTANGLE
        )

        assertTrue(result is AreaEstimationResult.Success)
        assertTrue(result.area > 0.0)
    }

    @Test
    fun `rectangle modification chooses nearest boundary without int overflow`() {
        assertTrue(
            updateRectangleBounds(
                BlockPos(0, 0, 0),
                listOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            ).contentEquals(intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE, 0, 0))
        )
    }

    @Test
    fun `scope modification rejects missing and excess selection points`() {
        val rectangle = GeoScope(
            "rectangle",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape(GeoShapeType.RECTANGLE, mutableListOf(0, 0, 100, 100))
        )
        val circle = GeoScope(
            "circle",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 100))
        )
        val error = AreaEstimationResult.Error(CreationError.InsufficientPoints)

        assertEquals(error, ScopeAreaChangeEstimator.estimateScopeModificationAreaChange(rectangle, emptyList()))
        assertEquals(
            error,
            ScopeAreaChangeEstimator.estimateScopeModificationAreaChange(
                rectangle,
                listOf(BlockPos.ZERO, BlockPos(1, 0, 1))
            )
        )
        assertEquals(
            error,
            ScopeAreaChangeEstimator.estimateScopeModificationAreaChange(
                circle,
                listOf(BlockPos.ZERO, BlockPos(1, 0, 0), BlockPos(2, 0, 0))
            )
        )
    }

    @Test
    fun `polygon area estimation rejects self intersecting turn sequence`() {
        val result = AreaEstimator.estimateShapeArea(
            listOf(
                BlockPos(0, 0, 10),
                BlockPos(6, 0, -8),
                BlockPos(-10, 0, 3),
                BlockPos(10, 0, 3),
                BlockPos(-6, 0, -8)
            ),
            GeoShapeType.POLYGON
        )

        assertEquals(AreaEstimationResult.Error(CreationError.NotConvex), result)
    }

    @Test
    fun `polygon area threshold is exact near int maximum coordinates`() {
        val maximum = Int.MAX_VALUE
        val positions = listOf(
            BlockPos(maximum - 50, 0, maximum - 195),
            BlockPos(maximum, 0, maximum - 195),
            BlockPos(maximum - 50, 0, maximum)
        )

        assertEquals(4875.0, com.imyvm.iwg.util.geo.calculatePolygonArea(positions))
        assertEquals(
            4875.0,
            GeoShape(
                GeoShapeType.POLYGON,
                positions.flatMap { listOf(it.x, it.z) }.toMutableList()
            ).calculateArea()
        )
        assertEquals(
            AreaEstimationResult.Error(CreationError.UnderSizeLimit),
            AreaEstimator.estimateShapeArea(positions, GeoShapeType.POLYGON)
        )
    }

    @Test
    fun `polygon area estimation rejects more than 256 vertices before geometry work`() {
        val positions = List(257) { BlockPos(it, 0, it) }

        assertEquals(
            AreaEstimationResult.Error(CreationError.PolygonVertexLimitExceeded),
            AreaEstimator.estimateShapeArea(positions, GeoShapeType.POLYGON)
        )
    }
}
