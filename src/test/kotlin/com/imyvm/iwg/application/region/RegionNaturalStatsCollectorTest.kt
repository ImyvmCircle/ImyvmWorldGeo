package com.imyvm.iwg.application.region

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RegionNaturalStatsCollectorTest {
    @Test
    fun `candidate chunk collection stops before exceeding budget`() {
        val chunks = linkedSetOf<Long>()

        val completed = RegionNaturalStatsCollector.addChunkRangeWithinLimit(
            chunks,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
            0,
            0,
            32
        )

        assertFalse(completed)
        assertEquals(32, chunks.size)
    }
}
