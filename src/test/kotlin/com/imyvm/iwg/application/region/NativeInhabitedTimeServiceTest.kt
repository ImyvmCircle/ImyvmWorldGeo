package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchRequest
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeCompleteness
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeSource
import net.minecraft.resources.Identifier
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NativeInhabitedTimeServiceTest {
    private val overworld = Identifier.parse("minecraft:overworld")
    private val nether = Identifier.parse("minecraft:the_nether")

    @Test
    fun `loaded and persisted chunks return native cumulative values without loading`() {
        val loaded = WorldGeoDimensionChunk(overworld, 0, 0)
        val persisted = WorldGeoDimensionChunk(overworld, 1, 0)
        var persistedReads = 0

        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(listOf(loaded, persisted), "geometry-v1"),
            1234L,
            { chunk -> if (chunk == loaded) 20L else null },
            {
                persistedReads++
                CompletableFuture.completedFuture(
                    NativeInhabitedTimeService.PersistedRead(
                        40L,
                        WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
                    )
                )
            }
        ).get()

        assertEquals(1, persistedReads)
        assertEquals("geometry-v1", result.inputVersion)
        assertEquals(20L, result.readings[0].inhabitedTicks)
        assertEquals(1000L, result.readings[0].inhabitedMillis)
        assertEquals(WorldGeoNativeInhabitedTimeSource.LOADED, result.readings[0].source)
        assertEquals(40L, result.readings[1].inhabitedTicks)
        assertEquals(2000L, result.readings[1].inhabitedMillis)
        assertEquals(WorldGeoNativeInhabitedTimeSource.PERSISTED, result.readings[1].source)
        assertEquals(1234L, result.readings[1].collectedAtMillis)
        assertEquals("geometry-v1", result.readings[1].inputVersion)
    }

    @Test
    fun `zero missing uninitialized corrupt and read failures remain distinct`() {
        val chunks = (0..4).map { WorldGeoDimensionChunk(overworld, it, 0) }
        val outcomes = listOf(
            CompletableFuture.completedFuture(
                NativeInhabitedTimeService.PersistedRead(
                    0L,
                    WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
                )
            ),
            CompletableFuture.completedFuture(
                NativeInhabitedTimeService.PersistedRead(
                    null,
                    WorldGeoNativeInhabitedTimeCompleteness.CHUNK_NOT_FOUND
                )
            ),
            CompletableFuture.completedFuture(
                NativeInhabitedTimeService.PersistedRead(
                    null,
                    WorldGeoNativeInhabitedTimeCompleteness.INDEX_NOT_INITIALIZED
                )
            ),
            CompletableFuture.failedFuture(EOFException("bad nbt")),
            CompletableFuture.failedFuture(IOException("disk failure"))
        )

        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(chunks, "input-v2"),
            9L,
            { null },
            { chunk -> outcomes[chunk.chunkX] }
        ).get()

        assertEquals(0L, result.readings[0].inhabitedTicks)
        assertEquals(0L, result.readings[0].inhabitedMillis)
        assertEquals(
            listOf(
                WorldGeoNativeInhabitedTimeCompleteness.COMPLETE,
                WorldGeoNativeInhabitedTimeCompleteness.CHUNK_NOT_FOUND,
                WorldGeoNativeInhabitedTimeCompleteness.INDEX_NOT_INITIALIZED,
                WorldGeoNativeInhabitedTimeCompleteness.CORRUPT_DATA,
                WorldGeoNativeInhabitedTimeCompleteness.READ_FAILED
            ),
            result.readings.map { it.completeness }
        )
        result.readings.drop(1).forEach {
            assertNull(it.inhabitedTicks)
            assertNull(it.inhabitedMillis)
            assertNull(it.source)
        }
    }

    @Test
    fun `negative values and exact millis overflow return failures`() {
        val negative = WorldGeoDimensionChunk(overworld, 0, 0)
        val overflow = WorldGeoDimensionChunk(nether, 0, 0)
        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(listOf(negative, overflow), "v3"),
            1L,
            { chunk -> if (chunk == negative) -1L else Long.MAX_VALUE },
            { error("persisted reader must not run") }
        ).get()

        assertEquals(
            WorldGeoNativeInhabitedTimeCompleteness.NEGATIVE_TICKS,
            result.readings[0].completeness
        )
        assertEquals(
            WorldGeoNativeInhabitedTimeCompleteness.MILLIS_OVERFLOW,
            result.readings[1].completeness
        )
    }

    @Test
    fun `batch is capped deduplicated across spaces and preserves dimensions`() {
        val shared = WorldGeoDimensionChunk(overworld, 3, 4)
        val otherDimension = WorldGeoDimensionChunk(nether, 3, 4)
        var reads = 0
        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(
                listOf(shared, shared, otherDimension),
                "adjacent-v1"
            ),
            2L,
            { null },
            {
                reads++
                CompletableFuture.completedFuture(
                    NativeInhabitedTimeService.PersistedRead(
                        1L,
                        WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
                    )
                )
            }
        ).get()

        assertEquals(2, reads)
        assertEquals(listOf(overworld, nether), result.readings.map { it.chunk.dimensionId })
        assertFailsWith<IllegalArgumentException> {
            NativeInhabitedTimeService.queryWithReaders(
                WorldGeoNativeInhabitedTimeBatchRequest(
                    (0..NativeInhabitedTimeService.MAX_CHUNKS).map {
                        WorldGeoDimensionChunk(overworld, it, 0)
                    },
                    "too-large"
                ),
                0L,
                { null },
                {
                    CompletableFuture.completedFuture(
                        NativeInhabitedTimeService.PersistedRead(
                            null,
                            WorldGeoNativeInhabitedTimeCompleteness.CHUNK_NOT_FOUND
                        )
                    )
                }
            )
        }
    }

    @Test
    fun `persisted reads are submitted in bounded pages`() {
        val chunks = (0..128).map { WorldGeoDimensionChunk(overworld, it, 0) }
        val pending = chunks.associateWith {
            CompletableFuture<NativeInhabitedTimeService.PersistedRead>()
        }
        var submitted = 0
        val result = NativeInhabitedTimeService.queryWithReaders(
            WorldGeoNativeInhabitedTimeBatchRequest(chunks, "paged"),
            3L,
            { null },
            { chunk ->
                submitted++
                pending.getValue(chunk)
            }
        )

        assertEquals(128, submitted)
        assertFalse(result.isDone)
        chunks.take(128).forEach {
            pending.getValue(it).complete(
                NativeInhabitedTimeService.PersistedRead(
                    1L,
                    WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
                )
            )
        }
        assertEquals(129, submitted)
        pending.getValue(chunks.last()).complete(
            NativeInhabitedTimeService.PersistedRead(
                2L,
                WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
            )
        )
        assertEquals(129, result.get().readings.size)
    }
}
