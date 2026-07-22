package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.TestPeriodModeStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPeriodModeServiceTest {
    @AfterTest
    fun tearDown() {
        TestPeriodModeStore.unbindSession()
    }

    @Test
    fun `active mode replaces every natural period kind with test periods`() = withTempDirectory { directory ->
        TestPeriodModeStore.bindSession(directory)
        TestPeriodModeService.start(clock = clock(0L))

        val ids = WorldGeoTimeService.currentNaturalPeriodIds(clock(840_000L))

        assertEquals("test:hour:168", ids[NaturalPeriodKind.HOUR])
        assertEquals("test:day:7", ids[NaturalPeriodKind.DAY])
        assertEquals("test:week:1", ids[NaturalPeriodKind.WEEK])
        assertEquals("test:month:0", ids[NaturalPeriodKind.MONTH])
    }

    @Test
    fun `default test hour lasts five seconds`() = withTempDirectory { directory ->
        TestPeriodModeStore.bindSession(directory)
        TestPeriodModeService.start(clock = clock(0L))

        assertEquals("test:hour:0", WorldGeoTimeService.currentNaturalPeriodIds(clock(4_999L))[NaturalPeriodKind.HOUR])
        assertEquals("test:hour:1", WorldGeoTimeService.currentNaturalPeriodIds(clock(5_000L))[NaturalPeriodKind.HOUR])
    }

    @Test
    fun `status reports configured mode bounds and remaining time`() = withTempDirectory { directory ->
        TestPeriodModeStore.bindSession(directory)
        TestPeriodModeService.start(weekCount = 5, clock = clock(1_000L))

        val status = TestPeriodModeService.status(clock(841_000L))

        assertEquals(true, status.active)
        assertEquals(840, status.weekLengthSeconds)
        assertEquals(1_000L, status.startedAtMillis)
        assertEquals(3_360_000L, status.remainingMillis)
        assertEquals(5, status.weekCount)
        assertEquals(2, status.currentWeek)
        assertEquals("test:week:1", status.periodIds[NaturalPeriodKind.WEEK])
    }

    private fun clock(millis: Long): Clock = Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-test-period-mode")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
