package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertTrue

class AreaEstimatorTest {
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
