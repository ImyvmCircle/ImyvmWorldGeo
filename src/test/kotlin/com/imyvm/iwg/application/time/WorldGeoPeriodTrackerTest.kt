package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.PeriodProcessingStore
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
    }

    @Test
    fun `first period sample initializes without transition`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:30:00Z"))

        assertEquals(emptyList(), transitions)
    }

    @Test
    fun `hour and day boundary emits changed periods`() = withTempDirectory { directory ->
        PeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:59:00Z"))
        WorldGeoPeriodTracker.process(clock("2026-07-20T16:00:00Z"))

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

        assertEquals(
            listOf(
                "HOUR:2026-07-20T21->2026-07-20T22",
                "HOUR:2026-07-20T22->2026-07-20T23",
                "HOUR:2026-07-20T23->2026-07-21T00"
            ),
            transitions
        )
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
