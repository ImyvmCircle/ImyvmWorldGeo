package com.imyvm.iwg.infra

import com.imyvm.iwg.application.time.TestPeriodModeService
import com.imyvm.iwg.application.time.WorldGeoPeriodTimelineService
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PeriodTimelineStoreTest {
    @AfterTest
    fun tearDown() {
        PeriodTimelineStore.unbindSession()
        TestPeriodModeStore.unbindSession()
    }

    @Test
    fun `repeated short test periods remain isolated and enumerable after restart`() =
        withTempDirectory { directory ->
            TestPeriodModeStore.bindSession(directory)
            PeriodTimelineStore.bindSession(directory, nowMillis = 0L)
            val productionKey = WorldGeoPeriodTimelineService.currentPeriodKeys(clock(0L))
                .getValue(NaturalPeriodKind.WEEK)

            TestPeriodModeService.start(clock = clock(1_000L))
            val first = WorldGeoPeriodTimelineService.currentPeriodKeys(clock(1_000L))
                .getValue(NaturalPeriodKind.WEEK)
            TestPeriodModeService.stop(clock(2_000L))

            TestPeriodModeService.start(clock = clock(3_000L))
            val second = WorldGeoPeriodTimelineService.currentPeriodKeys(clock(3_000L))
                .getValue(NaturalPeriodKind.WEEK)
            TestPeriodModeService.stop(clock(4_000L))

            assertEquals("test:week:0", first.periodId)
            assertEquals(first.periodId, second.periodId)
            assertNotEquals(first.timelineId, second.timelineId)
            assertEquals(PeriodTimelineStore.PRODUCTION_TIMELINE_ID, productionKey.timelineId)
            assertNotEquals(productionKey.timelineId, first.timelineId)

            PeriodTimelineStore.unbindSession()
            TestPeriodModeStore.unbindSession()
            TestPeriodModeStore.bindSession(directory)
            PeriodTimelineStore.bindSession(directory, nowMillis = 5_000L)

            val timelines = WorldGeoPeriodTimelineService.availableTimelines()
            assertEquals(listOf("production", "test-1", "test-2"), timelines.map { it.timelineId })
            assertTrue(timelines.filter { it.timelineId.startsWith("test-") }.all { it.closed })
            assertNotNull(
                WorldGeoPeriodTimelineService.availablePeriodRange(
                    first.timelineId,
                    NaturalPeriodKind.WEEK,
                    clock(5_000L)
                )
            )
            assertNotNull(
                WorldGeoPeriodTimelineService.availablePeriodRange(
                    second.timelineId,
                    NaturalPeriodKind.WEEK,
                    clock(5_000L)
                )
            )
        }

    @Test
    fun `period bounds support test periods and ISO weeks across calendar years`() =
        withTempDirectory { directory ->
            TestPeriodModeStore.bindSession(directory)
            PeriodTimelineStore.bindSession(directory, nowMillis = 0L)
            TestPeriodModeService.start(clock = clock(1_000L))
            val testKey = WorldGeoPeriodTimelineService.currentPeriodKeys(clock(1_000L))
                .getValue(NaturalPeriodKind.HOUR)

            val testBounds = assertNotNull(WorldGeoPeriodTimelineService.periodBounds(testKey))
            val productionBounds = assertNotNull(
                WorldGeoPeriodTimelineService.periodBounds(
                    NaturalPeriodKey("production", NaturalPeriodKind.WEEK, "2027-W01")
                )
            )

            assertEquals(1_000L, testBounds.startMillis)
            assertEquals(6_000L, testBounds.endMillis)
            assertEquals(
                Instant.parse("2027-01-03T16:00:00Z").toEpochMilli(),
                productionBounds.startMillis
            )
            assertEquals(
                Instant.parse("2027-01-10T16:00:00Z").toEpochMilli(),
                productionBounds.endMillis
            )
        }

    @Test
    fun `malformed timeline catalog is rejected without replacement`() = withTempDirectory { directory ->
        TestPeriodModeStore.bindSession(directory)
        val path = directory.resolve("iwg_period_timelines.json")
        val malformed = """{"formatVersion":1,"nextTestSequence":0,"timelines":[]}"""
        Files.writeString(path, malformed)

        assertFailsWith<IOException> {
            PeriodTimelineStore.bindSession(directory, nowMillis = 0L)
        }

        assertEquals(malformed, Files.readString(path))
    }

    private fun clock(millis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-period-timeline-test")
        try {
            block(directory)
        } finally {
            PeriodTimelineStore.unbindSession()
            TestPeriodModeStore.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }
}
