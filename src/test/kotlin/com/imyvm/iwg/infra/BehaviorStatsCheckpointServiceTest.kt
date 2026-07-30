package com.imyvm.iwg.infra

import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointRequest
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BehaviorStatsCheckpointServiceTest {
    @AfterTest
    fun tearDown() {
        BehaviorStatsCheckpointService.failureInjector = null
        SegmentedBehaviorStatsStore.failureInjector = null
        BehaviorStatsStore.clearForTest()
        PeriodTimelineStore.unbindSession()
        TestPeriodModeStore.unbindSession()
    }

    @Test
    fun `checkpoint cuts dirty stats on server dispatcher and retry preserves original evidence`() =
        withSessionDirectory { directory ->
            BehaviorStatsStore.record(event("stone"))
            val selected = mutableSetOf("stone")
            val request = request(objectIds = selected)
            val dispatcher = TestServerDispatcher()
            val future = BehaviorStatsCheckpointService.request(request, dispatcher::dispatch)

            dispatcher.runNext()
            selected.clear()
            selected.add("after")
            BehaviorStatsStore.record(event("stone"))
            val published = dispatcher.await(future)

            assertEquals(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, published.status)
            assertEquals(published.cutoffSequence, BehaviorStatsCheckpointService.publishedCheckpointSequence(CHECKPOINT_ID))
            assertEquals(1, published.pageCount)
            assertEquals(5, BehaviorStatsStore.pendingEntryCount())
            val page = assertNotNull(BehaviorStatsCheckpointService.readPage(CHECKPOINT_ID, 0).join())
            assertEquals(1L, page.entries.single().count)
            assertEquals("stone", page.entries.single().objectId)
            assertEquals(false, page.hasMore)

            BehaviorStatsStore.save()
            assertEquals(
                2L,
                BehaviorStatsStore.query(
                    WorldGeoBehaviorStatsQuery(
                        NaturalPeriodKind.HOUR,
                        PERIOD_ID,
                        regionId = 7,
                        objectId = "stone"
                    )
                ).single().count
            )
            BehaviorStatsStore.record(event("retry-dirty"))
            val retry = dispatcher.await(
                BehaviorStatsCheckpointService.request(
                    request(objectIds = setOf("stone")),
                    dispatcher::dispatch
                )
            )

            assertEquals(WorldGeoBehaviorStatsCheckpointStatus.ALREADY_PUBLISHED, retry.status)
            assertEquals(published.manifestVersion, retry.manifestVersion)
            assertEquals(5, BehaviorStatsStore.pendingEntryCount())
        }

    @Test
    fun `termination at every publication boundary never exposes a partial checkpoint`() {
        val servicePoints = listOf(
            "checkpoint:exchange",
            "checkpoint:page",
            "checkpoint:page-validation",
            "checkpoint:manifest"
        )
        servicePoints.forEach { point ->
            withSessionDirectory {
                BehaviorStatsStore.record(event("service-$point"))
                val dispatcher = TestServerDispatcher()
                BehaviorStatsCheckpointService.failureInjector = { current ->
                    if (current == point) throw IOException("terminated at $point")
                }

                assertFails {
                    dispatcher.await(
                        BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
                    )
                }
                assertNull(BehaviorStatsCheckpointService.publishedCheckpointSequence(CHECKPOINT_ID))

                BehaviorStatsCheckpointService.failureInjector = null
                val retry = dispatcher.await(
                    BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
                )
                assertEquals(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, retry.status)
            }
        }

        val storagePoints = listOf(
            "checkpoint:segment",
            "checkpoint:validation",
            "checkpoint:manifest"
        )
        storagePoints.forEach { point ->
            withSessionDirectory {
                BehaviorStatsStore.record(event("storage-$point"))
                val dispatcher = TestServerDispatcher()
                SegmentedBehaviorStatsStore.failureInjector = { current ->
                    if (current == point) throw IOException("terminated at $point")
                }

                assertFails {
                    dispatcher.await(
                        BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
                    )
                }
                assertEquals(0L, SegmentedBehaviorStatsStore.publishedSequence())
                assertNull(BehaviorStatsCheckpointService.publishedCheckpointSequence(CHECKPOINT_ID))
                assertEquals(5, BehaviorStatsStore.pendingEntryCount())

                SegmentedBehaviorStatsStore.failureInjector = null
                val retry = dispatcher.await(
                    BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
                )
                assertEquals(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, retry.status)
            }
        }
    }

    @Test
    fun `incomplete period is rejected without exchanging dirty stats`() = withSessionDirectory {
        BehaviorStatsStore.record(event("kept-dirty"))
        BehaviorCaptureControlStore.startMissing(EVENT_MILLIS)
        val dispatcher = TestServerDispatcher()

        val result = dispatcher.await(
            BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
        )

        assertEquals(WorldGeoBehaviorStatsCheckpointStatus.INCOMPLETE, result.status)
        assertEquals(WorldGeoPeriodDataStatus.INCOMPLETE, result.completeness.status)
        assertEquals(5, BehaviorStatsStore.pendingEntryCount())
        assertNull(BehaviorStatsCheckpointService.publishedCheckpointSequence(CHECKPOINT_ID))
    }

    @Test
    fun `retry reports version conflict when current totals are below checkpoint baseline`() =
        withSessionDirectory { directory ->
            BehaviorStatsStore.record(event("stone"))
            val dispatcher = TestServerDispatcher()
            val first = dispatcher.await(
                BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
            )
            assertEquals(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, first.status)
            increasePersistedBaseline(directory, 5L)

            val retry = dispatcher.await(
                BehaviorStatsCheckpointService.request(request(), dispatcher::dispatch)
            )

            assertEquals(WorldGeoBehaviorStatsCheckpointStatus.VERSION_CONFLICT, retry.status)
        }

    @Test
    fun `checkpoint paging remains bounded after historical growth`() = withSessionDirectory {
        repeat(64) { BehaviorStatsStore.record(event("object-$it")) }
        val dispatcher = TestServerDispatcher()

        val result = dispatcher.await(
            BehaviorStatsCheckpointService.request(request(pageSize = 1), dispatcher::dispatch)
        )

        assertEquals(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, result.status)
        assertEquals(64, result.pageCount)
        assertTrue(SegmentedBehaviorStatsStore.lastPageCandidateCount() <= 2)
        assertEquals(0, SegmentedBehaviorStatsStore.historicalEntryCountInMemory())
        assertEquals(0, BehaviorStatsStore.pendingEntryCount())
    }

    private fun request(
        objectIds: Set<String>? = null,
        pageSize: Int = 2_048
    ) = WorldGeoBehaviorStatsCheckpointRequest(
        CHECKPOINT_ID,
        WorldGeoBehaviorStatsPageQuery(
            PERIOD_KEY,
            7,
            objectIds = objectIds
        ),
        pageSize
    )

    private fun event(objectId: String) = WorldGeoBehaviorEvent(
        type = WorldGeoBehaviorType.DEBUG_TEST,
        playerUuid = PLAYER,
        playerName = "tester",
        dimensionId = Identifier.parse("minecraft:overworld"),
        x = 1,
        y = 64,
        z = 2,
        unixMillis = EVENT_MILLIS,
        regionId = 7,
        regionName = "region",
        scopeId = 7_001L,
        scopeName = "scope",
        subSpaceId = 9_001L,
        subSpaceName = "plot",
        objectId = objectId
    )

    private fun increasePersistedBaseline(directory: Path, count: Long) {
        val checkpoint = directory.resolve("iwg_behavior_checkpoints").resolve(CHECKPOINT_ID.toString())
        val pagePath = checkpoint.resolve("page-0.json")
        val page = JsonParser.parseString(Files.readString(pagePath)).asJsonArray
        page[0].asJsonObject.addProperty("count", count)
        val bytes = page.toString().toByteArray(Charsets.UTF_8)
        Files.write(pagePath, bytes)

        val manifestPath = checkpoint.resolve("manifest.json")
        val manifest = JsonParser.parseString(Files.readString(manifestPath)).asJsonObject
        val descriptor = manifest.getAsJsonArray("pages")[0].asJsonObject
        descriptor.addProperty("byteLength", bytes.size)
        descriptor.addProperty("checksum", sha256(bytes))
        Files.writeString(manifestPath, manifest.toString())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun withSessionDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-stats-checkpoint-test")
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

    private class TestServerDispatcher {
        private val tasks = LinkedBlockingQueue<() -> Unit>()

        fun dispatch(task: () -> Unit) {
            tasks.add(task)
        }

        fun runNext() {
            assertNotNull(tasks.poll(5, TimeUnit.SECONDS)).invoke()
        }

        fun <T> await(future: CompletableFuture<T>): T {
            while (!future.isDone) runNext()
            return future.join()
        }
    }

    private companion object {
        const val EVENT_MILLIS = 1_784_563_200_000L
        const val PERIOD_ID = "2026-07-21T00"
        val PERIOD_KEY = NaturalPeriodKey("production", NaturalPeriodKind.HOUR, PERIOD_ID)
        val CHECKPOINT_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val PLAYER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
