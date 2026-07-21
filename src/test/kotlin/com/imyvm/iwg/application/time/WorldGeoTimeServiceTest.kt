package com.imyvm.iwg.application.time

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGeoTimeServiceTest {
    @Test
    fun `real snapshot uses east eight natural periods`() {
        val snapshot = WorldGeoTimeService.realSnapshot(
            Instant.parse("2026-07-20T16:30:00Z"),
            WorldGeoTimeService.DEFAULT_ZONE
        )

        assertEquals(1_784_565_000L, snapshot.unixSeconds)
        assertEquals("+08:00", snapshot.zoneId)
        assertEquals("2026-07-21T00", snapshot.naturalHour)
        assertEquals("2026-07-21", snapshot.naturalDay)
        assertEquals("2026-W30", snapshot.naturalWeek)
        assertEquals("2026-07", snapshot.naturalMonth)
        assertEquals(2026, snapshot.naturalYear)
    }

    @Test
    fun `moon phase follows minecraft day cycle`() {
        assertEquals(0, WorldGeoTimeService.moonPhase(0L))
        assertEquals(1, WorldGeoTimeService.moonPhase(24_000L))
        assertEquals(7, WorldGeoTimeService.moonPhase(-24_000L))
    }
}
