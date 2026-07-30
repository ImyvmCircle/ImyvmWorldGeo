package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.WorldGeoBatchBlockDeltaStats
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPage
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageReadResult
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageReadStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsStreamOpenResult
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsStreamOpenStatus
import com.imyvm.iwg.domain.WorldGeoBlockDeltaBreakdown
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal object BehaviorStatsPageStreamService {
    const val DEFAULT_PAGE_SIZE = 2_048
    const val MAX_PAGE_SIZE = 8_192
    const val MAX_ACTIVE_HANDLES = 16
    const val IDLE_TIMEOUT_MILLIS = 300_000L
    const val MAX_CACHED_PAGE_COUNT = 32
    private val handles = linkedMapOf<UUID, HandleState>()
    private var cacheEvictions = 0L
    private val pageCache = object : LinkedHashMap<PageCacheKey, BehaviorStatsPageSlice>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<PageCacheKey, BehaviorStatsPageSlice>?
        ): Boolean {
            val remove = size > MAX_CACHED_PAGE_COUNT
            if (remove) cacheEvictions++
            return remove
        }
    }
    private var openingCount = 0
    private var acceptingHandles = false

    fun startSession() {
        synchronized(this) {
            acceptingHandles = true
            pageCache.clear()
            cacheEvictions = 0L
        }
    }

    fun open(
        query: WorldGeoBehaviorStatsPageQuery,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): CompletableFuture<WorldGeoBehaviorStatsStreamOpenResult> =
        open(query, pageSize, System.currentTimeMillis())

    internal fun open(
        query: WorldGeoBehaviorStatsPageQuery,
        pageSize: Int,
        nowMillis: Long
    ): CompletableFuture<WorldGeoBehaviorStatsStreamOpenResult> {
        val fixedQuery = freezeQuery(query)
        validateQuery(fixedQuery, pageSize)
        synchronized(this) {
            expireIdleHandlesLocked(nowMillis)
            if (!acceptingHandles) {
                return CompletableFuture.completedFuture(
                    WorldGeoBehaviorStatsStreamOpenResult(
                        WorldGeoBehaviorStatsStreamOpenStatus.CLOSED,
                        null,
                        null
                    )
                )
            }
            if (handles.size + openingCount >= MAX_ACTIVE_HANDLES) {
                return CompletableFuture.completedFuture(
                    WorldGeoBehaviorStatsStreamOpenResult(
                        WorldGeoBehaviorStatsStreamOpenStatus.BUSY,
                        null,
                        null
                    )
                )
            }
            openingCount++
            return openReserved(fixedQuery, pageSize, nowMillis)
        }
    }

    private fun openReserved(
        fixedQuery: WorldGeoBehaviorStatsPageQuery,
        pageSize: Int,
        nowMillis: Long
    ): CompletableFuture<WorldGeoBehaviorStatsStreamOpenResult> =
        SegmentedBehaviorStatsStore.submit {
            var snapshot: BehaviorStatsReadSnapshot? = null
            try {
                val completeness = BehaviorStatsStore.queryCompleteness(fixedQuery.periodKey)
                if (completeness.status == com.imyvm.iwg.domain.WorldGeoPeriodDataStatus.UNAVAILABLE) {
                    synchronized(this) { openingCount-- }
                    return@submit WorldGeoBehaviorStatsStreamOpenResult(
                        WorldGeoBehaviorStatsStreamOpenStatus.UNAVAILABLE,
                        null,
                        null
                    )
                }
                val fixedSnapshot = SegmentedBehaviorStatsStore.openSnapshot(fixedQuery)
                snapshot = fixedSnapshot
                val handleId = UUID.randomUUID()
                synchronized(this) {
                    openingCount--
                    handles[handleId] = HandleState(
                        fixedSnapshot,
                        fixedQuery,
                        pageSize,
                        completeness,
                        null,
                        nowMillis,
                        false
                    )
                }
                WorldGeoBehaviorStatsStreamOpenResult(
                    WorldGeoBehaviorStatsStreamOpenStatus.OPENED,
                    handleId,
                    fixedSnapshot.manifestVersion
                )
            } catch (error: Throwable) {
                snapshot?.let(SegmentedBehaviorStatsStore::closeSnapshot)
                synchronized(this) { openingCount-- }
                throw error
            }
        }

    fun nextPage(handleId: UUID): CompletableFuture<WorldGeoBehaviorStatsPageReadResult> {
        val state = synchronized(this) {
            val nowMillis = System.currentTimeMillis()
            expireIdleHandlesLocked(nowMillis)
            val current = handles[handleId]
                ?: return CompletableFuture.completedFuture(closedResult())
            if (current.reading) {
                return CompletableFuture.completedFuture(
                    WorldGeoBehaviorStatsPageReadResult(WorldGeoBehaviorStatsPageReadStatus.BUSY, null)
                )
            }
            val cacheKey = PageCacheKey(
                current.snapshot.manifestVersion,
                current.query,
                current.cursor,
                current.pageSize
            )
            pageCache[cacheKey]?.let { cached ->
                applySliceLocked(handleId, current, cached, nowMillis)
                return CompletableFuture.completedFuture(pageResult(current, cached))
            }
            current.reading = true
            current.lastAccessMillis = nowMillis
            current
        }
        val cacheKey = PageCacheKey(
            state.snapshot.manifestVersion,
            state.query,
            state.cursor,
            state.pageSize
        )
        return SegmentedBehaviorStatsStore.readPageAsync(
            state.snapshot,
            state.query,
            state.cursor,
            state.pageSize
        ).handle { slice, error ->
            synchronized(this) {
                state.reading = false
                if (error == null) {
                    pageCache[cacheKey] = slice
                    applySliceLocked(handleId, state, slice, System.currentTimeMillis())
                } else if (state.closeRequested) {
                    handles.remove(handleId)
                    SegmentedBehaviorStatsStore.closeSnapshot(state.snapshot)
                }
            }
            if (error != null) throw error
            pageResult(state, slice)
        }
    }

    fun close(handleId: UUID): CompletableFuture<Boolean> {
        val removed = synchronized(this) {
            val current = handles[handleId] ?: return CompletableFuture.completedFuture(false)
            if (current.reading) {
                current.closeRequested = true
                null
            } else {
                handles.remove(handleId)
            }
        }
        removed?.let { SegmentedBehaviorStatsStore.closeSnapshot(it.snapshot) }
        return CompletableFuture.completedFuture(true)
    }

    fun queryBlockDeltaBatch(
        query: WorldGeoBehaviorStatsPageQuery
    ): CompletableFuture<WorldGeoBatchBlockDeltaStats> {
        val fixedQuery = freezeQuery(query)
        validateQuery(fixedQuery, DEFAULT_PAGE_SIZE)
        return synchronized(this) {
            if (!acceptingHandles) {
                return CompletableFuture.failedFuture(
                    IllegalStateException("Behavior stats session is not active")
                )
            }
            SegmentedBehaviorStatsStore.submit {
                val completeness = BehaviorStatsStore.queryCompleteness(fixedQuery.periodKey)
                if (completeness.status == com.imyvm.iwg.domain.WorldGeoPeriodDataStatus.UNAVAILABLE) {
                    return@submit WorldGeoBatchBlockDeltaStats(
                        fixedQuery.periodKey,
                        fixedQuery.regionId,
                        fixedQuery.scopeId,
                        fixedQuery.subSpaceId,
                        emptyMap(),
                        completeness,
                        SegmentedBehaviorStatsStore.manifestVersion()
                    )
                }
                val snapshot = SegmentedBehaviorStatsStore.openSnapshot(fixedQuery)
                try {
                val aggregate = SegmentedBehaviorStatsStore.scanBlockDelta(snapshot, fixedQuery)
                WorldGeoBatchBlockDeltaStats(
                    fixedQuery.periodKey,
                    fixedQuery.regionId,
                    fixedQuery.scopeId,
                    fixedQuery.subSpaceId,
                    aggregate.blocks.mapValues { (_, block) ->
                        WorldGeoBlockDeltaBreakdown(
                            block.placedCount,
                            block.brokenCount,
                            Math.subtractExact(block.placedCount, block.brokenCount),
                            block.playerContributions.toMap()
                        )
                    },
                    completeness,
                    snapshot.manifestVersion
                )
                } finally {
                    SegmentedBehaviorStatsStore.closeSnapshot(snapshot)
                }
            }
        }
    }

    private fun applySliceLocked(
        handleId: UUID,
        state: HandleState,
        slice: BehaviorStatsPageSlice,
        nowMillis: Long
    ) {
        state.cursor = slice.nextCursor
        state.lastAccessMillis = nowMillis
        if (state.closeRequested || !slice.hasMore) {
            handles.remove(handleId)
            SegmentedBehaviorStatsStore.closeSnapshot(state.snapshot)
        }
    }

    private fun pageResult(state: HandleState, slice: BehaviorStatsPageSlice) =
        WorldGeoBehaviorStatsPageReadResult(
            WorldGeoBehaviorStatsPageReadStatus.PAGE,
            WorldGeoBehaviorStatsPage(
                state.snapshot.manifestVersion,
                slice.entries,
                slice.hasMore,
                state.completeness
            )
        )

    fun expireIdleHandles(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(this) { expireIdleHandlesLocked(nowMillis) }
    }

    internal fun activeHandleCount(): Int = synchronized(this) { handles.size + openingCount }

    internal fun cachedPageCount(): Int = synchronized(this) { pageCache.size }

    internal fun cacheEvictionCount(): Long = synchronized(this) { cacheEvictions }

    fun closeAllHandles() {
        val immediate = synchronized(this) {
            acceptingHandles = false
            val ready = handles.filterValues { !it.reading }
            handles.values.filter { it.reading }.forEach { it.closeRequested = true }
            ready.keys.forEach(handles::remove)
            ready.values.toList()
        }
        immediate.forEach { SegmentedBehaviorStatsStore.closeSnapshot(it.snapshot) }
        SegmentedBehaviorStatsStore.awaitIdle()
        val remaining = synchronized(this) {
            val active = handles.values.toList()
            handles.clear()
            openingCount = 0
            pageCache.clear()
            active
        }
        remaining.forEach { SegmentedBehaviorStatsStore.closeSnapshot(it.snapshot) }
    }

    private fun expireIdleHandlesLocked(nowMillis: Long) {
        val expired = handles.filterValues {
            !it.reading && nowMillis >= it.lastAccessMillis &&
                nowMillis - it.lastAccessMillis >= IDLE_TIMEOUT_MILLIS
        }
        expired.forEach { (id, state) ->
            handles.remove(id)
            SegmentedBehaviorStatsStore.closeSnapshot(state.snapshot)
        }
    }

    private fun freezeQuery(query: WorldGeoBehaviorStatsPageQuery) = query.copy(
        objectIds = query.objectIds?.toSet(),
        playerUuids = query.playerUuids?.toSet()
    )

    private fun validateQuery(query: WorldGeoBehaviorStatsPageQuery, pageSize: Int) {
        require(pageSize in 1..MAX_PAGE_SIZE) { "page size must be between 1 and $MAX_PAGE_SIZE" }
        require(query.periodKey.timelineId.isNotBlank()) { "timeline id must not be blank" }
        require(query.periodKey.periodId.isNotBlank()) { "period id must not be blank" }
        require(query.regionId > 0) { "region id must be positive" }
        require(query.scopeId == null || query.scopeId != 0L) { "scope id must not be zero" }
        require(query.subSpaceId == null || query.subSpaceId > 0L) { "subspace id must be positive" }
        require(query.objectIds == null || query.objectIds.all(String::isNotBlank)) {
            "object ids must not contain blank values"
        }
    }

    private fun closedResult() =
        WorldGeoBehaviorStatsPageReadResult(WorldGeoBehaviorStatsPageReadStatus.CLOSED, null)

    private data class HandleState(
        val snapshot: BehaviorStatsReadSnapshot,
        val query: WorldGeoBehaviorStatsPageQuery,
        val pageSize: Int,
        val completeness: com.imyvm.iwg.domain.WorldGeoPeriodCompleteness,
        var cursor: BehaviorStatsKey?,
        var lastAccessMillis: Long,
        var reading: Boolean,
        var closeRequested: Boolean = false
    )

    private data class PageCacheKey(
        val manifestVersion: Long,
        val query: WorldGeoBehaviorStatsPageQuery,
        val cursor: BehaviorStatsKey?,
        val pageSize: Int
    )
}
