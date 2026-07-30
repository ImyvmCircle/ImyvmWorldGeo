package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorCaptureState
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import com.imyvm.iwg.infra.config.CoreConfig
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BehaviorCaptureReliabilityTest {
    private val defaultMaxEntryCount = CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.value
    private val defaultWarningEntryCount = CoreConfig.BEHAVIOR_STATS_WARNING_ENTRY_COUNT.value
    private val defaultMaxEstimatedBytes = CoreConfig.BEHAVIOR_STATS_MAX_ESTIMATED_BYTES.value
    private val defaultWarningEstimatedBytes = CoreConfig.BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES.value

    @AfterTest
    fun tearDown() {
        BehaviorStatsStore.clearForTest()
        PeriodTimelineStore.unbindSession()
        TestPeriodModeStore.unbindSession()
        CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.setValue(defaultMaxEntryCount)
        CoreConfig.BEHAVIOR_STATS_WARNING_ENTRY_COUNT.setValue(defaultWarningEntryCount)
        CoreConfig.BEHAVIOR_STATS_MAX_ESTIMATED_BYTES.setValue(defaultMaxEstimatedBytes)
        CoreConfig.BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES.setValue(defaultWarningEstimatedBytes)
    }

    @Test
    fun `failed writes reach hard limit with constant missing state and recover at watermark`() =
        withSessionDirectory { directory ->
            CoreConfig.BEHAVIOR_STATS_WARNING_ENTRY_COUNT.setValue(5)
            CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.setValue(5)
            BehaviorStatsStore.bindSession(directory, nowMillis = EVENT_MILLIS - 1_000L)
            SegmentedBehaviorStatsStore.failureInjector = { point ->
                if (point == "append:manifest") throw IOException("storage unavailable")
            }
            BehaviorStatsStore.record(event("first"))
            assertEquals(WorldGeoBehaviorCaptureState.ACTIVE, BehaviorStatsStore.captureState())
            assertNotNull(BehaviorStatsStore.storageAlert())
            assertFailsWith<IOException> { BehaviorStatsStore.save() }

            BehaviorStatsStore.record(event("missing"))
            repeat(1_000) { BehaviorStatsStore.record(event("missing-" + it)) }

            assertEquals(WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED, BehaviorStatsStore.captureState())
            assertEquals(1L, BehaviorStatsStore.query(hourQuery("first")).single().count)
            assertEquals(1_001L, assertNotNull(BehaviorStatsStore.activeMissingInterval()).droppedEventCount)
            assertNotNull(BehaviorStatsStore.storageAlert())

            SegmentedBehaviorStatsStore.failureInjector = null
            BehaviorStatsStore.save(nowMillis = EVENT_MILLIS + 1_000L)

            assertEquals(WorldGeoBehaviorCaptureState.ACTIVE, BehaviorStatsStore.captureState())
            assertNull(BehaviorStatsStore.storageAlert())
            assertEquals(emptyList(), BehaviorStatsStore.query(hourQuery("missing")))
            BehaviorStatsStore.unbindSession(nowMillis = EVENT_MILLIS + 2_000L)
            BehaviorStatsStore.bindSession(directory, nowMillis = EVENT_MILLIS + 3_000L)
            val completeness = BehaviorStatsStore.queryCompleteness(
                NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "2026-07-21T00")
            )
            assertEquals(WorldGeoPeriodDataStatus.INCOMPLETE, completeness.status)
            assertEquals(1_001L, completeness.missingIntervals.single().droppedEventCount)
        }

    @Test
    fun `estimated byte hard limit suspends capture without growing pending keys`() =
        withSessionDirectory { directory ->
            CoreConfig.BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES.setValue(1)
            CoreConfig.BEHAVIOR_STATS_MAX_ESTIMATED_BYTES.setValue(1)
            BehaviorStatsStore.bindSession(directory)

            BehaviorStatsStore.record(event("too-large"))

            assertEquals(WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED, BehaviorStatsStore.captureState())
            assertEquals(0L, BehaviorStatsStore.estimatedPendingBytes())
            assertEquals(1L, assertNotNull(BehaviorStatsStore.activeMissingInterval()).droppedEventCount)
        }

    @Test
    fun `abnormal restart records an incomplete session interval`() = withSessionDirectory { directory ->
        BehaviorStatsStore.bindSession(directory, nowMillis = 1_000L)
        BehaviorStatsStore.abandonSessionForTest()
        BehaviorStatsStore.bindSession(directory, nowMillis = 3_000L)

        val completeness = epochHourCompleteness()

        assertEquals(WorldGeoPeriodDataStatus.INCOMPLETE, completeness.status)
        assertEquals(1_000L, completeness.missingIntervals.single().startMillis)
        assertEquals(3_000L, completeness.missingIntervals.single().endMillis)
    }

    @Test
    fun `normal restart keeps session period complete`() = withSessionDirectory { directory ->
        BehaviorStatsStore.bindSession(directory, nowMillis = 1_000L)
        BehaviorStatsStore.unbindSession(nowMillis = 2_000L)
        BehaviorStatsStore.bindSession(directory, nowMillis = 3_000L)

        val completeness = epochHourCompleteness()

        assertEquals(WorldGeoPeriodDataStatus.COMPLETE, completeness.status)
        assertEquals(emptyList(), completeness.missingIntervals)
    }

    @Test
    fun `shutdown save failure prevents a clean session marker`() = withSessionDirectory { directory ->
        BehaviorStatsStore.bindSession(directory, nowMillis = 1_000L)
        BehaviorStatsStore.record(event("unsaved"))
        SegmentedBehaviorStatsStore.failureInjector = { point ->
            if (point == "append:manifest") throw IOException("shutdown storage failure")
        }
        assertFailsWith<IOException> { BehaviorStatsStore.save() }
        BehaviorStatsStore.markSessionUnclean()
        BehaviorStatsStore.unbindSession(nowMillis = 2_000L)
        BehaviorStatsStore.bindSession(directory, nowMillis = 3_000L)

        assertEquals(WorldGeoPeriodDataStatus.INCOMPLETE, epochHourCompleteness().status)
    }

    @Test
    fun `malformed session marker is rejected without replacement`() = withSessionDirectory { directory ->
        val control = directory.resolve("iwg_behavior_stats/control")
        Files.createDirectories(control.resolve("missing"))
        val marker = control.resolve("session.json")
        val malformed = "{}"
        Files.writeString(marker, malformed)

        assertFailsWith<IOException> { BehaviorStatsStore.bindSession(directory, nowMillis = 3_000L) }
        assertEquals(malformed, Files.readString(marker))
    }

    private fun epochHourCompleteness() = BehaviorStatsStore.queryCompleteness(
        NaturalPeriodKey("production", NaturalPeriodKind.HOUR, "1970-01-01T08"),
        3_000L
    )

    private fun hourQuery(objectId: String) = WorldGeoBehaviorStatsQuery(
        NaturalPeriodKind.HOUR,
        "2026-07-21T00",
        regionId = 7,
        objectId = objectId
    )

    private fun event(objectId: String) = WorldGeoBehaviorEvent(
        type = WorldGeoBehaviorType.DEBUG_TEST,
        playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        playerName = "tester",
        dimensionId = Identifier.parse("minecraft:overworld"),
        x = 1,
        y = 64,
        z = 2,
        unixMillis = EVENT_MILLIS,
        regionId = 7,
        regionName = "region",
        scopeId = 7001L,
        scopeName = "scope",
        subSpaceId = 9001L,
        subSpaceName = "plot",
        objectId = objectId
    )

    private fun withSessionDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-behavior-capture-test")
        try {
            TestPeriodModeStore.bindSession(directory)
            PeriodTimelineStore.bindSession(directory, nowMillis = 0L)
            block(directory)
        } finally {
            BehaviorStatsStore.clearForTest()
            PeriodTimelineStore.unbindSession()
            TestPeriodModeStore.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val EVENT_MILLIS = 1_784_563_200_000L
    }
}
