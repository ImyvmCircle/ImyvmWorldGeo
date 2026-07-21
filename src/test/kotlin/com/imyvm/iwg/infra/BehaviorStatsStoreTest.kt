package com.imyvm.iwg.infra

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BehaviorStatsStoreTest {
    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @AfterTest
    fun tearDown() {
        WorldGeoBehaviorEventBus.clearForTest()
        BehaviorStatsStore.clearForTest()
    }

    @Test
    fun `records behavior stats for natural periods and filters query`() = withTempDirectory { directory ->
        BehaviorStatsStore.bindSession(directory)

        BehaviorStatsStore.record(event(WorldGeoBehaviorType.BLOCK_PLACE, objectId = "minecraft:stone"))
        BehaviorStatsStore.record(event(WorldGeoBehaviorType.BLOCK_PLACE, objectId = "minecraft:stone"))
        BehaviorStatsStore.record(event(WorldGeoBehaviorType.BLOCK_BREAK, objectId = "minecraft:dirt"))

        val entries = BehaviorStatsStore.query(
            WorldGeoBehaviorStatsQuery(
                periodKind = NaturalPeriodKind.HOUR,
                periodId = "2026-07-21T00",
                behaviorType = WorldGeoBehaviorType.BLOCK_PLACE,
                regionId = 7,
                scopeId = 7001,
                subSpaceId = 9001,
                playerUuid = playerUuid,
                objectId = "minecraft:stone"
            )
        )

        assertEquals(1, entries.size)
        assertEquals(2, entries.single().count)
    }

    @Test
    fun `event bus publish records behavior stats`() = withTempDirectory { directory ->
        BehaviorStatsStore.bindSession(directory)

        WorldGeoBehaviorEventBus.publish(event(WorldGeoBehaviorType.DEBUG_TEST, objectId = "debug"))

        val entries = BehaviorStatsStore.query(WorldGeoBehaviorStatsQuery(NaturalPeriodKind.HOUR, "2026-07-21T00", regionId = 7))
        assertEquals(1, entries.size)
        assertEquals(1, entries.single().count)
    }

    @Test
    fun `round trips behavior stats`() = withTempDirectory { directory ->
        BehaviorStatsStore.bindSession(directory)
        BehaviorStatsStore.record(event(WorldGeoBehaviorType.DEBUG_TEST, objectId = "debug"))
        BehaviorStatsStore.save()
        BehaviorStatsStore.unbindSession()

        BehaviorStatsStore.bindSession(directory)
        val entries = BehaviorStatsStore.query(WorldGeoBehaviorStatsQuery(NaturalPeriodKind.DAY, "2026-07-21", regionId = 7))

        assertEquals(1, entries.size)
        assertEquals(WorldGeoBehaviorType.DEBUG_TEST, entries.single().behaviorType)
        assertEquals(1, entries.single().count)
    }

    @Test
    fun `rejects malformed behavior stats file`() = withTempDirectory { directory ->
        Files.writeString(directory.resolve("iwg_behavior_stats.json"), "{}")

        assertFailsWith<IOException> { BehaviorStatsStore.bindSession(directory) }
    }

    @Test
    fun `writer rejects invalid count without replacing existing file`() = withTempDirectory { directory ->
        val path = directory.resolve("iwg_behavior_stats.json")
        val key = BehaviorStatsKey(NaturalPeriodKind.HOUR, "2026-07-21T00", WorldGeoBehaviorType.DEBUG_TEST, 7, null, null, playerUuid, "debug")
        BehaviorStatsStore.writeStats(path, mapOf(key to 1L))
        val original = Files.readString(path)

        assertFailsWith<IllegalArgumentException> { BehaviorStatsStore.writeStats(path, mapOf(key to 0L)) }
        assertEquals(original, Files.readString(path))
    }

    private fun event(type: WorldGeoBehaviorType, objectId: String?) = WorldGeoBehaviorEvent(
        type = type,
        playerUuid = playerUuid,
        playerName = "tester",
        dimensionId = Identifier.parse("minecraft:overworld"),
        x = 1,
        y = 64,
        z = 2,
        unixMillis = 1_784_563_200_000L,
        regionId = 7,
        regionName = "region",
        scopeId = 7001,
        scopeName = "scope",
        subSpaceId = 9001,
        subSpaceName = "plot",
        objectId = objectId
    )

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-behavior-stats-test")
        try {
            block(directory)
        } finally {
            WorldGeoBehaviorEventBus.clearForTest()
            BehaviorStatsStore.clearForTest()
            directory.toFile().deleteRecursively()
        }
    }
}
