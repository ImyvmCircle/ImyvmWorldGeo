package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.TestPeriodProcessingStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldGeoTestPeriodTrackerTest {
    @AfterTest
    fun tearDown() {
        WorldGeoTestPeriodTracker.resetForTest()
        TestPeriodProcessingStore.unbindSession()
    }

    @Test
    fun `first test period tick initializes independent periods`() = withTempDirectory { directory ->
        TestPeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<NaturalPeriodKind>()
        WorldGeoTestPeriodTracker.registerCallback { transitions.add(it.kind) }

        val emitted = WorldGeoTestPeriodTracker.process(clockAtMillis(0L))

        assertEquals(0, emitted)
        assertEquals(emptyList(), transitions)
        assertEquals("test5m:week:0", TestPeriodProcessingStore.getProcessedPeriodIds()[NaturalPeriodKind.WEEK])
    }

    @Test
    fun `test week is five minutes`() = withTempDirectory { directory ->
        TestPeriodProcessingStore.bindSession(directory)
        val transitions = mutableListOf<String>()
        WorldGeoTestPeriodTracker.registerCallback { transitions.add("${it.kind}:${it.previousId}->${it.currentId}") }

        WorldGeoTestPeriodTracker.process(clockAtMillis(0L))
        val emitted = WorldGeoTestPeriodTracker.process(clockAtMillis(300_000L))

        assertTrue(emitted >= 1)
        assertTrue("WEEK:test5m:week:0->test5m:week:1" in transitions)
    }

    @Test
    fun `event periods are inactive until test tracker initializes`() = withTempDirectory { directory ->
        TestPeriodProcessingStore.bindSession(directory)

        assertEquals(emptyMap(), WorldGeoTestPeriodTracker.activePeriodIdsForEvent(300_000L))

        WorldGeoTestPeriodTracker.process(clockAtMillis(0L))

        assertEquals("test5m:week:1", WorldGeoTestPeriodTracker.activePeriodIdsForEvent(300_000L)[NaturalPeriodKind.WEEK])
    }

    private fun clockAtMillis(value: Long): Clock = Clock.fixed(Instant.ofEpochMilli(value), ZoneOffset.UTC)

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-test-period-tracker")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
