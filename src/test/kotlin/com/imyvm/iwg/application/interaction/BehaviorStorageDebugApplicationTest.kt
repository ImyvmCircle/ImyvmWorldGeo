package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.WorldGeoBehaviorCaptureState
import com.imyvm.iwg.infra.BehaviorStatsStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehaviorStorageDebugApplicationTest {
    private var root: Path? = null

    @AfterTest
    fun tearDown() {
        BehaviorStatsStore.clearForTest()
        root?.takeIf(Files::exists)?.let { path ->
            Files.walk(path).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `storage snapshot exposes bounded health queue segment and cache facts after restart`() {
        val path = Files.createTempDirectory("worldgeo-storage-debug")
        root = path
        BehaviorStatsStore.bindSession(path, 1_000L)

        val first = behaviorStorageDebugSnapshot()

        assertEquals("HEALTHY", first.health)
        assertEquals(WorldGeoBehaviorCaptureState.ACTIVE, first.captureState)
        assertEquals(0, first.pendingEntries)
        assertEquals(0L, first.pendingBytes)
        assertTrue(first.thresholds.contains("recovery=75%"))
        assertTrue(first.ioQueue >= 0)
        assertTrue(first.ioActive >= 0)
        assertEquals(0, first.segmentCount)
        assertTrue(first.cache.contains("pages=0/32"))
        assertTrue(first.cache.contains("handles=0/16"))

        BehaviorStatsStore.unbindSession(2_000L)
        BehaviorStatsStore.bindSession(path, 3_000L)
        val restarted = behaviorStorageDebugSnapshot()

        assertEquals("HEALTHY", restarted.health)
        assertEquals(first.manifestVersion, restarted.manifestVersion)
        assertEquals(0, restarted.segmentCount)
    }
}
