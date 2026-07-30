package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegmentedBehaviorStatsStoreTest {
    @AfterTest
    fun tearDown() {
        SegmentedBehaviorStatsStore.unbindSession()
    }

    @Test
    fun `legacy JSON migrates once with exact per-key counts`() = withTempDirectory { directory ->
        val legacyPath = directory.resolve("iwg_behavior_stats.json")
        val first = key(NaturalPeriodKind.DAY, "2026-07-30", objectId = "first")
        val second = key(NaturalPeriodKind.MONTH, "2026-07", objectId = "second")
        BehaviorStatsStore.writeStats(legacyPath, mapOf(first to 3L, second to 7L))

        SegmentedBehaviorStatsStore.bindSession(directory, legacyPath)
        assertEquals(mapOf(first to 3L, second to 7L), SegmentedBehaviorStatsStore.readAll())
        SegmentedBehaviorStatsStore.unbindSession()

        BehaviorStatsStore.writeStats(legacyPath, mapOf(first to 99L))
        SegmentedBehaviorStatsStore.bindSession(directory, legacyPath)

        assertEquals(mapOf(first to 3L, second to 7L), SegmentedBehaviorStatsStore.readAll())
    }

    @Test
    fun `segment and manifest interruption never publish a partial batch`() = withTempDirectory { directory ->
        bindEmpty(directory)
        SegmentedBehaviorStatsStore.failureInjector = { point ->
            if (point == "append:segment") throw IOException("segment interrupted")
        }
        assertFailsWith<IOException> {
            SegmentedBehaviorStatsStore.append(mapOf(key() to 1L), 1L, 1L)
        }
        assertTrue(SegmentedBehaviorStatsStore.readAll().isEmpty())

        SegmentedBehaviorStatsStore.failureInjector = { point ->
            if (point == "append:manifest") throw IOException("manifest interrupted")
        }
        assertFailsWith<IOException> {
            SegmentedBehaviorStatsStore.append(mapOf(key() to 1L), 1L, 1L)
        }
        assertTrue(SegmentedBehaviorStatsStore.readAll().isEmpty())
    }

    @Test
    fun `checksum failure and duplicate sequence are rejected`() = withTempDirectory { directory ->
        bindEmpty(directory)
        SegmentedBehaviorStatsStore.append(mapOf(key() to 2L), 1L, 1L)

        assertFails {
            SegmentedBehaviorStatsStore.append(mapOf(key(objectId = "duplicate") to 1L), 1L, 1L)
        }
        val segment = segmentFiles(directory).single()
        Files.writeString(segment, Files.readString(segment) + "corrupt")

        assertFailsWith<IOException> { SegmentedBehaviorStatsStore.readAll() }
    }

    @Test
    fun `compression failure keeps old segments and successful retry preserves totals`() =
        withTempDirectory { directory ->
            bindEmpty(directory)
            SegmentedBehaviorStatsStore.append(mapOf(key() to 2L), 1L, 1L)
            SegmentedBehaviorStatsStore.append(mapOf(key() to 3L), 2L, 2L)
            SegmentedBehaviorStatsStore.failureInjector = { point ->
                if (point == "compression:manifest") throw IOException("compression interrupted")
            }

            assertFailsWith<IOException> {
                SegmentedBehaviorStatsStore.compact("production", NaturalPeriodKind.HOUR, "2026-07-30T00", 7)
            }
            assertEquals(5L, SegmentedBehaviorStatsStore.readAll().getValue(key()))

            SegmentedBehaviorStatsStore.failureInjector = null
            SegmentedBehaviorStatsStore.compact("production", NaturalPeriodKind.HOUR, "2026-07-30T00", 7)
            assertEquals(5L, SegmentedBehaviorStatsStore.readAll().getValue(key()))
            assertEquals(1, segmentFiles(directory).size)
        }

    @Test
    fun `restart keeps historical entries off heap and reads them only on stats IO thread`() =
        withTempDirectory { directory ->
            bindEmpty(directory)
            SegmentedBehaviorStatsStore.append(mapOf(key() to 1L), 1L, 1L)
            SegmentedBehaviorStatsStore.unbindSession()
            SegmentedBehaviorStatsStore.bindSession(directory, directory.resolve("missing.json"))

            assertEquals(0, SegmentedBehaviorStatsStore.historicalEntryCountInMemory())
            SegmentedBehaviorStatsStore.readAll()
            assertEquals("worldgeo-stats-io", SegmentedBehaviorStatsStore.lastHistoricalReadThread())
        }

    @Test
    fun `appending another period never rewrites an old month segment`() = withTempDirectory { directory ->
        bindEmpty(directory)
        val month = key(NaturalPeriodKind.MONTH, "2026-07")
        SegmentedBehaviorStatsStore.append(mapOf(month to 4L), 1L, 1L)
        val monthFile = segmentFiles(directory).single()
        val original = Files.readAllBytes(monthFile)

        SegmentedBehaviorStatsStore.append(mapOf(key() to 1L), 2L, 2L)

        assertTrue(original.contentEquals(Files.readAllBytes(monthFile)))
    }

    private fun bindEmpty(directory: Path) {
        SegmentedBehaviorStatsStore.bindSession(directory, directory.resolve("missing.json"))
    }

    private fun segmentFiles(directory: Path): List<Path> =
        Files.walk(directory.resolve("iwg_behavior_stats")).use { paths ->
            paths.filter { it.fileName.toString().startsWith("segment-") }.toList()
        }

    private fun key(
        kind: NaturalPeriodKind = NaturalPeriodKind.HOUR,
        periodId: String = "2026-07-30T00",
        objectId: String = "minecraft:stone"
    ) = BehaviorStatsKey(
        kind,
        periodId,
        WorldGeoBehaviorType.BLOCK_PLACE,
        7,
        7001L,
        null,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        objectId
    )

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-segmented-stats-test")
        try {
            block(directory)
        } finally {
            SegmentedBehaviorStatsStore.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }
}
