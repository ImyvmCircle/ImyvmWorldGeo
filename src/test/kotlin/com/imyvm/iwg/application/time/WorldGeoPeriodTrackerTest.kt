package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.PeriodProcessingStore
import com.imyvm.iwg.infra.TestPeriodModeStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGeoPeriodTrackerTest {
    @AfterTest
    fun tearDown() {
        WorldGeoPeriodTracker.resetForTest()
        PeriodProcessingStore.unbindSession()
        TestPeriodModeStore.unbindSession()
    }

    @Test
    fun `first period sample initializes without transition`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:30:00Z"))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(emptyList(), transitions)
    }

    @Test
    fun `hour and day boundary emits changed periods`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:59:00Z"))
        WorldGeoPeriodTracker.process(clock("2026-07-20T16:00:00Z"))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(listOf(NaturalPeriodKind.HOUR, NaturalPeriodKind.DAY), transitions)
    }

    @Test
    fun `stored previous period emits missed boundary on first sample`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        PeriodProcessingStore.replaceProcessedPeriodIds(
            mapOf(
                NaturalPeriodKind.HOUR to "2026-07-20T23",
                NaturalPeriodKind.DAY to "2026-07-20",
                NaturalPeriodKind.WEEK to "2026-W30",
                NaturalPeriodKind.MONTH to "2026-07"
            )
        )
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T16:00:00Z"))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(listOf(NaturalPeriodKind.HOUR, NaturalPeriodKind.DAY), transitions)
    }

    @Test
    fun `stored previous period emits every missed hour boundary`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        PeriodProcessingStore.replaceProcessedPeriodIds(
            mapOf(
                NaturalPeriodKind.HOUR to "2026-07-20T21",
                NaturalPeriodKind.DAY to "2026-07-21",
                NaturalPeriodKind.WEEK to "2026-W30",
                NaturalPeriodKind.MONTH to "2026-07"
            )
        )
        val transitions = mutableListOf<String>()
        WorldGeoPeriodTracker.registerCallback { transitions.add("${it.kind}:${it.previousId}->${it.currentId}") }

        WorldGeoPeriodTracker.process(clock("2026-07-20T16:00:00Z"))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(
            listOf(
                "HOUR:2026-07-20T21->2026-07-20T22",
                "HOUR:2026-07-20T22->2026-07-20T23",
                "HOUR:2026-07-20T23->2026-07-21T00"
            ),
            transitions
        )
    }


    @Test
    fun `test mode emits test period transitions without changing production store`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        TestPeriodModeStore.bindSession(directory)
        TestPeriodModeService.start(clock = clock("2026-07-20T15:59:00Z"))
        val transitions = mutableListOf<String>()
        WorldGeoPeriodTracker.registerCallback { transitions.add("${it.kind}:${it.previousId}->${it.currentId}") }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:59:00Z"))
        WorldGeoPeriodTracker.process(Clock.fixed(Instant.parse("2026-07-20T15:59:05Z"), ZoneOffset.UTC))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(listOf("HOUR:test:hour:0->test:hour:1"), transitions)
        assertEquals(emptyMap(), PeriodProcessingStore.getProcessedPeriodIds())
    }

    @Test
    fun `expired test mode resumes natural periods without production backfill`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        TestPeriodModeStore.bindSession(directory)
        TestPeriodModeService.start(clock = clock("2026-07-20T15:59:00Z"))
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:59:00Z"))
        WorldGeoPeriodTracker.process(clock("2026-07-20T16:41:01Z"))
        WorldGeoPeriodTracker.awaitCallbacksForTest()

        assertEquals(emptyList(), transitions)
        assertEquals(
            mapOf(
                NaturalPeriodKind.HOUR to "2026-07-21T00",
                NaturalPeriodKind.DAY to "2026-07-21",
                NaturalPeriodKind.WEEK to "2026-W30",
                NaturalPeriodKind.MONTH to "2026-07"
            ),
            PeriodProcessingStore.getProcessedPeriodIds()
        )
        assertEquals(null, TestPeriodModeStore.currentState())
    }

    private fun clock(value: String): Clock = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-period-tracker")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
