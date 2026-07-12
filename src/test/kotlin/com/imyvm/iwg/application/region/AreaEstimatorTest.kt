package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AreaEstimatorTest {
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
    fun `rectangle area handles int boundary coordinates`() {
        val result = AreaEstimator.estimateShapeArea(
            listOf(BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE), BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)),
            GeoShapeType.RECTANGLE
        )

        assertTrue(result is AreaEstimationResult.Success)
        assertTrue(result.area > 0.0)
    }
}
