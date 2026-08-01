package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsEntry
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageReadStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsStreamOpenStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BehaviorStatsPageStreamServiceTest {
    @AfterTest
    fun tearDown() {
        BehaviorStatsPageStreamService.closeAllHandles()
        BehaviorStatsStore.clearForTest()
        PeriodTimelineStore.unbindSession()
        TestPeriodModeStore.unbindSession()
    }

    @Test
    fun `async pages match synchronous query and apply exact space and set filters`() =
        withSessionDirectory { directory ->
            BehaviorStatsStore.record(event("stone", PLAYER_ONE, 7_001L, 9_001L))
            BehaviorStatsStore.record(event("dirt", PLAYER_TWO, 7_001L, 9_002L))
            BehaviorStatsStore.record(event("gold", PLAYER_ONE, 7_002L, null))
            BehaviorStatsStore.save()

            val oldEntries = BehaviorStatsStore.query(
                WorldGeoBehaviorStatsQuery(NaturalPeriodKind.HOUR, PERIOD_ID, regionId = 7)
            )
            val (pages, newEntries) = collectPages(query(), pageSize = 2)

            assertEquals(oldEntries.toComparableSet(), newEntries.toComparableSet())
            assertTrue(pages.all { it.entries.size <= 2 })
            assertTrue(SegmentedBehaviorStatsStore.lastPageCandidateCount() <= 3)
            assertEquals("worldgeo-stats-io", SegmentedBehaviorStatsStore.lastHistoricalReadThread())
            assertEquals(1, collectPages(query(scopeId = 7_001L, subSpaceId = 9_001L)).second.size)
            assertEquals(2, collectPages(query(scopeId = 7_001L)).second.size)
            assertEquals(
                setOf("stone"),
                collectPages(query(objectIds = setOf("stone"), playerUuids = setOf(PLAYER_ONE)))
                    .second.mapNotNull { it.objectId }.toSet()
            )
            assertEquals(emptyList(), collectPages(query(objectIds = emptySet())).second)
            assertEquals(emptyList(), collectPages(query(objectIds = setOf("unknown"))).second)
        }

    @Test
    fun `handle keeps manifest version stable across append and compression`() =
        withSessionDirectory { directory ->
            BehaviorStatsStore.record(event("a"))
            BehaviorStatsStore.save()
            BehaviorStatsStore.record(event("b"))
            BehaviorStatsStore.save()
            val selectedObjects = mutableSetOf("a", "b")
            val opened = BehaviorStatsPageStreamService.open(
                query(objectIds = selectedObjects),
                1
            ).join()
            val handleId = assertNotNull(opened.handleId)
            val fixedVersion = assertNotNull(opened.manifestVersion)
            selectedObjects.clear()
            selectedObjects.add("c")

            BehaviorStatsStore.record(event("c"))
            BehaviorStatsStore.save()
            SegmentedBehaviorStatsStore.compact(
                "production",
                NaturalPeriodKind.HOUR,
                PERIOD_ID,
                7
            )

            val entries = mutableListOf<WorldGeoBehaviorStatsEntry>()
            var hasMore: Boolean
            do {
                val page = assertNotNull(BehaviorStatsPageStreamService.nextPage(handleId).join().page)
                assertEquals(fixedVersion, page.manifestVersion)
                entries += page.entries
                hasMore = page.hasMore
            } while (hasMore)

            assertEquals(setOf("a", "b"), entries.mapNotNull { it.objectId }.toSet())
            assertFalse(entries.any { it.objectId == "c" })
        }

    @Test
    fun `duplicate keys across segments are aggregated before paging`() = withSessionDirectory {
        BehaviorStatsStore.record(event("same"))
        BehaviorStatsStore.save()
        BehaviorStatsStore.record(event("same"))
        BehaviorStatsStore.save()

        val entries = collectPages(query(objectIds = setOf("same")), pageSize = 1).second

        assertEquals(1, entries.size)
        assertEquals(2L, entries.single().count)
    }

    @Test
    fun `active handle limit returns structured busy and idle handles close`() =
        withSessionDirectory {
            val handles = (1..BehaviorStatsPageStreamService.MAX_ACTIVE_HANDLES).map {
                val opened = BehaviorStatsPageStreamService.open(query(objectIds = emptySet()), 1).join()
                assertEquals(WorldGeoBehaviorStatsStreamOpenStatus.OPENED, opened.status)
                assertNotNull(opened.handleId)
            }

            val busy = BehaviorStatsPageStreamService.open(query(), 1).join()

            assertEquals(WorldGeoBehaviorStatsStreamOpenStatus.BUSY, busy.status)
            assertEquals(null, busy.handleId)
            handles.forEach { assertTrue(BehaviorStatsPageStreamService.close(it).join()) }

            val idle = BehaviorStatsPageStreamService.open(query(), 1, nowMillis = 1_000L).join()
            val idleId = assertNotNull(idle.handleId)
            BehaviorStatsPageStreamService.expireIdleHandles(
                1_000L + BehaviorStatsPageStreamService.IDLE_TIMEOUT_MILLIS
            )
            assertEquals(
                WorldGeoBehaviorStatsPageReadStatus.CLOSED,
                BehaviorStatsPageStreamService.nextPage(idleId).join().status
            )
        }

    @Test
    fun `page read yields asynchronously and close waits for the active read`() = withSessionDirectory {
        BehaviorStatsStore.record(event("slow"))
        BehaviorStatsStore.save()
        val opened = BehaviorStatsPageStreamService.open(query(), 1).join()
        val handleId = assertNotNull(opened.handleId)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        SegmentedBehaviorStatsStore.scanInjector = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            val activeRead = BehaviorStatsPageStreamService.nextPage(handleId)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(activeRead.isDone)
            assertEquals(
                WorldGeoBehaviorStatsPageReadStatus.BUSY,
                BehaviorStatsPageStreamService.nextPage(handleId).join().status
            )
            assertTrue(BehaviorStatsPageStreamService.close(handleId).join())
            release.countDown()
            assertEquals(WorldGeoBehaviorStatsPageReadStatus.PAGE, activeRead.join().status)
            assertEquals(
                WorldGeoBehaviorStatsPageReadStatus.CLOSED,
                BehaviorStatsPageStreamService.nextPage(handleId).join().status
            )
        } finally {
            release.countDown()
            SegmentedBehaviorStatsStore.scanInjector = null
        }
    }

    @Test
    fun `batch block delta scans blocks and players together`() = withSessionDirectory {
        BehaviorStatsStore.record(event("stone", type = WorldGeoBehaviorType.BLOCK_PLACE))
        BehaviorStatsStore.record(event("stone", type = WorldGeoBehaviorType.BLOCK_PLACE))
        BehaviorStatsStore.record(event("stone", type = WorldGeoBehaviorType.BLOCK_BREAK))
        BehaviorStatsStore.record(event("dirt", PLAYER_TWO, type = WorldGeoBehaviorType.BLOCK_PLACE))
        BehaviorStatsStore.save()

        val result = BehaviorStatsPageStreamService.queryBlockDeltaBatch(
            query(objectIds = setOf("stone", "dirt"))
        ).join()

        assertEquals(2L, result.blocks.getValue("stone").placedCount)
        assertEquals(1L, result.blocks.getValue("stone").brokenCount)
        assertEquals(1L, result.blocks.getValue("stone").netDelta)
        assertEquals(1L, result.blocks.getValue("stone").playerContributions[PLAYER_ONE])
        assertEquals(1L, result.blocks.getValue("dirt").playerContributions[PLAYER_TWO])
    }

    @Test
    fun `supported addon API delegates production block delta batch`() = withSessionDirectory {
        BehaviorStatsStore.record(event("stone", type = WorldGeoBehaviorType.BLOCK_PLACE))
        BehaviorStatsStore.record(event("stone", type = WorldGeoBehaviorType.BLOCK_BREAK))
        BehaviorStatsStore.record(event("dirt", PLAYER_TWO, type = WorldGeoBehaviorType.BLOCK_PLACE))
        BehaviorStatsStore.save()

        val result = RegionDataApi.queryProductionBlockDeltaBatchAsync(
            NaturalPeriodKind.HOUR,
            PERIOD_ID,
            7,
            setOf("stone", "dirt")
        ).join()

        assertEquals(1L, result.blocks.getValue("stone").placedCount)
        assertEquals(1L, result.blocks.getValue("stone").brokenCount)
        assertEquals(0L, result.blocks.getValue("stone").netDelta)
        assertEquals(1L, result.blocks.getValue("dirt").playerContributions[PLAYER_TWO])
    }

    @Test
    fun `supported addon API delegates asynchronous open read and close`() = withSessionDirectory {
        BehaviorStatsStore.record(event("api-a"))
        BehaviorStatsStore.record(event("api-b"))
        BehaviorStatsStore.save()

        val opened = RegionDataApi.openBehaviorStatsPageStream(query(), 1).join()
        val handleId = assertNotNull(opened.handleId)
        val read = RegionDataApi.readBehaviorStatsPage(handleId).join()

        assertEquals(WorldGeoBehaviorStatsPageReadStatus.PAGE, read.status)
        assertTrue(assertNotNull(read.page).hasMore)
        assertTrue(RegionDataApi.closeBehaviorStatsPageStream(handleId).join())
    }

    @Test
    fun `page cache evicts eldest entries at its fixed bound`() = withSessionDirectory {
        repeat(BehaviorStatsPageStreamService.MAX_CACHED_PAGE_COUNT + 1) {
            collectPages(query(objectIds = setOf("unknown-" + it)), pageSize = 1)
        }

        assertEquals(BehaviorStatsPageStreamService.MAX_CACHED_PAGE_COUNT,
            BehaviorStatsPageStreamService.cachedPageCount())
        assertEquals(1L, BehaviorStatsPageStreamService.cacheEvictionCount())
    }

    @Test
    fun `open outside a world session reports closed rather than busy`() {
        BehaviorStatsPageStreamService.closeAllHandles()

        val opened = BehaviorStatsPageStreamService.open(query(), 1).join()

        assertEquals(WorldGeoBehaviorStatsStreamOpenStatus.CLOSED, opened.status)
    }

    @Test
    fun `page size has a strict maximum`() = withSessionDirectory {
        assertFailsWith<IllegalArgumentException> {
            BehaviorStatsPageStreamService.open(
                query(),
                BehaviorStatsPageStreamService.MAX_PAGE_SIZE + 1
            )
        }
    }

    private fun collectPages(
        query: WorldGeoBehaviorStatsPageQuery,
        pageSize: Int = BehaviorStatsPageStreamService.DEFAULT_PAGE_SIZE
    ): Pair<List<com.imyvm.iwg.domain.WorldGeoBehaviorStatsPage>, List<WorldGeoBehaviorStatsEntry>> {
        val opened = BehaviorStatsPageStreamService.open(query, pageSize).join()
        assertEquals(WorldGeoBehaviorStatsStreamOpenStatus.OPENED, opened.status)
        val handleId = assertNotNull(opened.handleId)
        val pages = mutableListOf<com.imyvm.iwg.domain.WorldGeoBehaviorStatsPage>()
        do {
            val read = BehaviorStatsPageStreamService.nextPage(handleId).join()
            assertEquals(WorldGeoBehaviorStatsPageReadStatus.PAGE, read.status)
            pages += assertNotNull(read.page)
        } while (pages.last().hasMore)
        return pages to pages.flatMap { it.entries }
    }

    private fun query(
        scopeId: Long? = null,
        subSpaceId: Long? = null,
        objectIds: Set<String>? = null,
        playerUuids: Set<UUID>? = null
    ) = WorldGeoBehaviorStatsPageQuery(
        PERIOD_KEY,
        7,
        scopeId,
        subSpaceId,
        null,
        objectIds,
        playerUuids
    )

    private fun event(
        objectId: String,
        playerUuid: UUID = PLAYER_ONE,
        scopeId: Long = 7_001L,
        subSpaceId: Long? = 9_001L,
        type: WorldGeoBehaviorType = WorldGeoBehaviorType.DEBUG_TEST
    ) = WorldGeoBehaviorEvent(
        type = type,
        playerUuid = playerUuid,
        playerName = "tester",
        dimensionId = Identifier.parse("minecraft:overworld"),
        x = 1,
        y = 64,
        z = 2,
        unixMillis = EVENT_MILLIS,
        regionId = 7,
        regionName = "region",
        scopeId = scopeId,
        scopeName = "scope",
        subSpaceId = subSpaceId,
        subSpaceName = "plot",
        objectId = objectId
    )

    private fun List<WorldGeoBehaviorStatsEntry>.toComparableSet() = map {
        listOf(
            it.periodKind,
            it.periodId,
            it.behaviorType,
            it.regionId,
            it.scopeId,
            it.subSpaceId,
            it.playerUuid,
            it.objectId,
            it.targetId,
            it.count
        )
    }.toSet()

    private fun withSessionDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-stats-page-stream-test")
        try {
            TestPeriodModeStore.bindSession(directory)
            PeriodTimelineStore.bindSession(directory, nowMillis = 0L)
            BehaviorStatsStore.bindSession(directory, nowMillis = EVENT_MILLIS - 1_000L)
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
        const val PERIOD_ID = "2026-07-21T00"
        val PERIOD_KEY = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, PERIOD_ID)
        val PLAYER_ONE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val PLAYER_TWO: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
