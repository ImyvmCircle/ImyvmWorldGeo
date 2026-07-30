package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchRequest
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeInhabitedTimeLimitTest {
    @Test
    fun `exactly 4096 unique chunks are accepted`() {
        val dimensionId = Identifier.parse("minecraft:overworld")
        val chunks = (0 until NativeInhabitedTimeService.MAX_CHUNKS).map {
            WorldGeoDimensionChunk(dimensionId, it, 0)
        }

        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(chunks, "limit"),
            5L,
            { 0L },
            { error("persisted reader must not run") }
        ).get()

        assertEquals(NativeInhabitedTimeService.MAX_CHUNKS, result.readings.size)
    }
}
