package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
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
    }

    @Test
    fun `first period sample initializes without transition`() {
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:30:00Z"))

        assertEquals(emptyList(), transitions)
    }

    @Test
    fun `hour and day boundary emits changed periods`() {
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoPeriodTracker.registerCallback { transitions.add(it.kind) }

        WorldGeoPeriodTracker.process(clock("2026-07-20T15:59:00Z"))
        WorldGeoPeriodTracker.process(clock("2026-07-20T16:00:00Z"))

        assertEquals(listOf(NaturalPeriodKind.HOUR, NaturalPeriodKind.DAY), transitions)
    }

    private fun clock(value: String): Clock = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)
}
