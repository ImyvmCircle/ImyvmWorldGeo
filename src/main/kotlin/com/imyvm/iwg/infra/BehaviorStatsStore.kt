package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.time.TestPeriodModeService
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.application.time.WorldGeoPeriodTimelineService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsEntry
import com.imyvm.iwg.domain.WorldGeoBlockDeltaStats
import com.imyvm.iwg.domain.WorldGeoCombatPlayerStats
import com.imyvm.iwg.domain.WorldGeoCombatTargetStats
import com.imyvm.iwg.domain.WorldGeoEntityCombatStats
import com.imyvm.iwg.domain.WorldGeoOnlineTimeStats
import com.imyvm.iwg.domain.WorldGeoPlayerOnlineTimeStats
import com.imyvm.iwg.domain.WorldGeoResidenceStats
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.WorldGeoBehaviorCaptureState
import com.imyvm.iwg.domain.WorldGeoPeriodCompleteness
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import com.imyvm.iwg.infra.config.CoreConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object BehaviorStatsStore {
    private const val FILE_NAME = "iwg_behavior_stats.json"
    private const val RESIDENCE_CHUNK_PREFIX = "residence_chunk:"
    private const val ONLINE_OBJECT_ID = "online_millis"
    private const val AFK_OBJECT_ID = "afk_millis"
    private const val DAMAGED_OBJECT_ID = "damaged"
    private var sessionWorldRoot: Path? = null
    private val counts = linkedMapOf<BehaviorStatsKey, Long>()
    private val warnedCapacityActions = linkedSetOf<String>()
    private var nextWriteSequence = 1L
    private var dirtySequenceStart: Long? = null
    private var dirtySequenceEnd = 0L
    private var estimatedPendingBytes = 0L
    private var captureState = WorldGeoBehaviorCaptureState.ACTIVE
    private var warningActive = false
    private var cleanCloseAllowed = true

    internal fun bindSession(worldRoot: Path, nowMillis: Long = System.currentTimeMillis()) {
        check(sessionWorldRoot == null) { "Behavior stats store session is already active" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        counts.clear()
        warnedCapacityActions.clear()
        SegmentedBehaviorStatsStore.bindSession(root, root.resolve(FILE_NAME))
        try {
            BehaviorStatsCheckpointService.bindSession(root)
            BehaviorCaptureControlStore.bindSession(root, nowMillis)
        } catch (error: Throwable) {
            BehaviorStatsCheckpointService.unbindSession()
            SegmentedBehaviorStatsStore.unbindSession()
            BehaviorCaptureControlStore.resetForTest()
            throw error
        }
        nextWriteSequence = Math.addExact(SegmentedBehaviorStatsStore.publishedSequence(), 1L)
        dirtySequenceStart = null
        dirtySequenceEnd = 0L
        estimatedPendingBytes = 0L
        captureState = WorldGeoBehaviorCaptureState.ACTIVE
        warningActive = false
        cleanCloseAllowed = true
        sessionWorldRoot = root
        BehaviorStatsPageStreamService.startSession()
    }

    internal fun unbindSession(nowMillis: Long = System.currentTimeMillis()) {
        counts.clear()
        warnedCapacityActions.clear()
        BehaviorStatsPageStreamService.closeAllHandles()
        BehaviorStatsCheckpointService.unbindSession()
        if (cleanCloseAllowed) {
            BehaviorCaptureControlStore.closeSession(nowMillis)
        } else {
            BehaviorCaptureControlStore.abandonSession()
        }
        SegmentedBehaviorStatsStore.unbindSession()
        BehaviorCaptureControlStore.resetForTest()
        nextWriteSequence = 1L
        dirtySequenceStart = null
        dirtySequenceEnd = 0L
        estimatedPendingBytes = 0L
        captureState = WorldGeoBehaviorCaptureState.ACTIVE
        warningActive = false
        cleanCloseAllowed = true
        sessionWorldRoot = null
    }

    fun record(event: WorldGeoBehaviorEvent) {
        record(event, event.quantity)
    }

    fun recordResidenceMillis(
        event: WorldGeoBehaviorEvent,
        chunkX: Int,
        chunkZ: Int,
        millis: Long
    ) {
        require(millis > 0L) { "millis must be positive" }
        record(event.copy(type = WorldGeoBehaviorType.SPACE_ENTER, objectId = "$RESIDENCE_CHUNK_PREFIX$chunkX,$chunkZ", targetId = null), millis)
    }

    fun recordOnlineMillis(event: WorldGeoBehaviorEvent, millis: Long, afk: Boolean = false) {
        require(millis > 0L) { "millis must be positive" }
        record(event.copy(type = WorldGeoBehaviorType.ITEM_USE, objectId = if (afk) AFK_OBJECT_ID else ONLINE_OBJECT_ID, targetId = null), millis)
    }

    fun recordDamagedPlayer(event: WorldGeoBehaviorEvent, attackerId: String?) {
        record(event.copy(type = WorldGeoBehaviorType.ENTITY_DAMAGE, objectId = DAMAGED_OBJECT_ID, targetId = attackerId), 1L)
    }

    internal fun recordDebugCount(event: WorldGeoBehaviorEvent, count: Long) {
        require(count > 0L) { "count must be positive" }
        record(event, count)
    }

    private fun record(event: WorldGeoBehaviorEvent, count: Long) {
        val regionId = event.regionId ?: return
        val scopeId = event.scopeId ?: return
        if (regionId <= 0) {
            errorOnce(
                "invalid-region:${event.type}:$regionId",
                "Dropped behavior stats event with invalid region id: type=${event.type} regionId=$regionId scopeId=${event.scopeId} subSpaceId=${event.subSpaceId} playerUuid=${event.playerUuid} source=${event.source} pos=${event.x},${event.y},${event.z} dimension=${event.dimensionId}"
            )
            return
        }
        if (scopeId == 0L) {
            errorOnce(
                "invalid-scope:${event.type}:$scopeId",
                "Dropped behavior stats event with invalid scope id: type=${event.type} regionId=$regionId scopeId=$scopeId subSpaceId=${event.subSpaceId} playerUuid=${event.playerUuid} source=${event.source} pos=${event.x},${event.y},${event.z} dimension=${event.dimensionId}"
            )
            return
        }
        val subSpaceId = event.subSpaceId
        if (subSpaceId != null && subSpaceId <= 0L) {
            errorOnce(
                "invalid-subspace:${event.type}:$subSpaceId",
                "Dropped behavior stats event with invalid subspace id: type=${event.type} regionId=$regionId scopeId=${event.scopeId} subSpaceId=$subSpaceId playerUuid=${event.playerUuid} source=${event.source} pos=${event.x},${event.y},${event.z} dimension=${event.dimensionId}"
            )
            return
        }
        val clock = Clock.fixed(Instant.ofEpochMilli(event.unixMillis), ZoneOffset.UTC)
        val periodKeys = WorldGeoTimeService.currentNaturalPeriodKeys(clock)
        val keys = periodKeys.map { (periodKind, completeKey) ->
            BehaviorStatsKey(
                periodKind = periodKind,
                periodId = completeKey.periodId,
                behaviorType = event.type,
                regionId = regionId,
                scopeId = scopeId,
                subSpaceId = event.subSpaceId,
                playerUuid = event.playerUuid,
                objectId = event.objectId,
                targetId = event.targetId,
                timelineId = completeKey.timelineId
            )
        }
        recordKeys(keys, count, event.unixMillis)
    }

    fun captureState(): WorldGeoBehaviorCaptureState = captureState

    fun storageAlert(): String? = when {
        captureState == WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED ->
            "[IMYVMWorldGeo] Behavior statistics capture is suspended; affected periods are incomplete."
        warningActive ->
            "[IMYVMWorldGeo] Behavior statistics pending storage is above its warning threshold."
        else -> null
    }

    fun queryCompleteness(key: NaturalPeriodKey): WorldGeoPeriodCompleteness {
        val bounds = requireNotNull(WorldGeoPeriodTimelineService.periodBounds(key)) { "Unknown natural period: $key" }
        val missing = BehaviorCaptureControlStore.intersecting(bounds)
        return WorldGeoPeriodCompleteness(
            key,
            if (missing.isEmpty()) WorldGeoPeriodDataStatus.COMPLETE else WorldGeoPeriodDataStatus.INCOMPLETE,
            missing
        )
    }

    internal fun estimatedPendingBytes(): Long = estimatedPendingBytes

    internal fun activeMissingInterval() = BehaviorCaptureControlStore.activeMissingInterval()

    fun query(query: WorldGeoBehaviorStatsQuery): List<WorldGeoBehaviorStatsEntry> {
        require(query.periodId.isNotBlank()) { "period id must not be blank" }
        val persisted = SegmentedBehaviorStatsStore.readAll()
        val merged = LinkedHashMap(persisted)
        counts.forEach { (key, count) -> merged[key] = Math.addExact(merged[key] ?: 0L, count) }
        val expectedTimeline = if (TestPeriodModeService.isTestPeriodId(query.periodId)) {
            runCatching { PeriodTimelineStore.activeTimeline().timelineId }.getOrNull()
        } else PeriodTimelineStore.PRODUCTION_TIMELINE_ID
        return merged.asSequence()
            .filter { (key, _) -> key.matches(query) && key.timelineId == expectedTimeline }
            .map { (key, count) -> key.toEntry(count) }
            .toList()
    }

    fun queryBlockDelta(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        blockFilter: String?
    ): WorldGeoBlockDeltaStats {
        val placed = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.BLOCK_PLACE, regionId, scopeId, subSpaceId, objectId = blockFilter))
        val broken = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.BLOCK_BREAK, regionId, scopeId, subSpaceId, objectId = blockFilter))
        val contributions = linkedMapOf<UUID, Long>()
        placed.forEach { contributions[it.playerUuid] = Math.addExact(contributions[it.playerUuid] ?: 0L, it.count) }
        broken.forEach { contributions[it.playerUuid] = Math.subtractExact(contributions[it.playerUuid] ?: 0L, it.count) }
        val placedCount = placed.sumOf { it.count }
        val brokenCount = broken.sumOf { it.count }
        return WorldGeoBlockDeltaStats(
            periodKind = periodKind,
            periodId = periodId,
            regionId = regionId,
            scopeId = scopeId,
            subSpaceId = subSpaceId,
            blockFilter = blockFilter,
            placedCount = placedCount,
            brokenCount = brokenCount,
            netDelta = Math.subtractExact(placedCount, brokenCount),
            playerContributions = contributions.toMap()
        )
    }

    fun queryResidence(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?
    ): WorldGeoResidenceStats {
        val entries = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.SPACE_ENTER, regionId, scopeId, subSpaceId))
            .filter { it.objectId?.startsWith(RESIDENCE_CHUNK_PREFIX) == true }
        val chunks = linkedMapOf<String, Long>()
        entries.forEach { entry ->
            val chunkId = entry.objectId?.removePrefix(RESIDENCE_CHUNK_PREFIX) ?: return@forEach
            chunks[chunkId] = Math.addExact(chunks[chunkId] ?: 0L, entry.count)
        }
        val total = chunks.values.sum()
        return WorldGeoResidenceStats(
            periodKind = periodKind,
            periodId = periodId,
            regionId = regionId,
            scopeId = scopeId,
            subSpaceId = subSpaceId,
            chunkResidenceMillis = chunks.toMap(),
            averageResidenceMillis = if (chunks.isEmpty()) 0L else total / chunks.size,
            totalResidenceMillis = total
        )
    }

    fun queryEntityCombat(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        objectFilter: String?
    ): WorldGeoEntityCombatStats = queryEntityCombat(periodKind, periodId, regionId, scopeId, subSpaceId, objectFilter, null)

    fun queryEntityCombat(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        objectFilter: String?,
        targetFilter: String?
    ): WorldGeoEntityCombatStats {
        val damage = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.ENTITY_DAMAGE, regionId, scopeId, subSpaceId, objectId = objectFilter, targetId = targetFilter))
        val kills = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.ENTITY_KILL, regionId, scopeId, subSpaceId, objectId = objectFilter, targetId = targetFilter))
        val deaths = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.PLAYER_DEATH, regionId, scopeId, subSpaceId, objectId = objectFilter, targetId = targetFilter))
        val damaged = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.ENTITY_DAMAGE, regionId, scopeId, subSpaceId, objectId = DAMAGED_OBJECT_ID, targetId = targetFilter))
        val players = linkedMapOf<UUID, LongArray>()
        val targets = linkedMapOf<String, LongArray>()
        damage.forEach { entry ->
            players.getOrPut(entry.playerUuid) { LongArray(4) }[0] += entry.count
            entry.targetId?.let { targets.getOrPut(it) { LongArray(2) }[0] += entry.count }
        }
        kills.forEach { entry ->
            players.getOrPut(entry.playerUuid) { LongArray(4) }[1] += entry.count
            entry.targetId?.let { targets.getOrPut(it) { LongArray(2) }[1] += entry.count }
        }
        deaths.forEach { players.getOrPut(it.playerUuid) { LongArray(4) }[2] += it.count }
        damaged.forEach { players.getOrPut(it.playerUuid) { LongArray(4) }[3] += it.count }
        return WorldGeoEntityCombatStats(
            periodKind = periodKind,
            periodId = periodId,
            regionId = regionId,
            scopeId = scopeId,
            subSpaceId = subSpaceId,
            objectFilter = objectFilter,
            targetFilter = targetFilter,
            damageCount = damage.sumOf { it.count },
            killCount = kills.sumOf { it.count },
            deathCount = deaths.sumOf { it.count },
            damagedCount = damaged.sumOf { it.count },
            playerStats = players.mapValues { (_, values) ->
                WorldGeoCombatPlayerStats(values[0], values[1], values[2], values[3])
            },
            targetStats = targets.mapValues { (_, values) ->
                WorldGeoCombatTargetStats(values[0], values[1])
            }
        )
    }

    fun queryOnlineTime(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID?
    ): WorldGeoOnlineTimeStats {
        val online = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.ITEM_USE, regionId, scopeId, subSpaceId, playerUuid, ONLINE_OBJECT_ID))
        val afk = query(WorldGeoBehaviorStatsQuery(periodKind, periodId, WorldGeoBehaviorType.ITEM_USE, regionId, scopeId, subSpaceId, playerUuid, AFK_OBJECT_ID))
        val players = linkedMapOf<UUID, LongArray>()
        online.forEach { players.getOrPut(it.playerUuid) { LongArray(2) }[0] += it.count }
        afk.forEach { players.getOrPut(it.playerUuid) { LongArray(2) }[1] += it.count }
        val playerStats = players.mapValues { (_, values) ->
            val nonAfk = (values[0] - values[1]).coerceAtLeast(0L)
            WorldGeoPlayerOnlineTimeStats(values[0], values[1], nonAfk)
        }
        val totalOnline = playerStats.values.sumOf { it.onlineMillis }
        val totalAfk = playerStats.values.sumOf { it.afkMillis }
        return WorldGeoOnlineTimeStats(
            periodKind = periodKind,
            periodId = periodId,
            regionId = regionId,
            scopeId = scopeId,
            subSpaceId = subSpaceId,
            playerFilter = playerUuid,
            totalOnlineMillis = totalOnline,
            totalAfkMillis = totalAfk,
            totalNonAfkMillis = (totalOnline - totalAfk).coerceAtLeast(0L),
            playerStats = playerStats
        )
    }

    fun saveSnapshot() {
        if (sessionWorldRoot == null) return
        runCatching { save() }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to save behavior stats: ${it.message}", it) }
    }

    internal fun save(nowMillis: Long = System.currentTimeMillis()) {
        sessionWorldRoot ?: error("Behavior stats store session is not active")
        val start = dirtySequenceStart
        var wrotePending = false
        if (start != null) {
            SegmentedBehaviorStatsStore.append(counts.toMap(), start, dirtySequenceEnd)
            counts.clear()
            dirtySequenceStart = null
            estimatedPendingBytes = 0L
            wrotePending = true
        }
        if (wrotePending && captureState == WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED && belowRecoveryWatermark()) {
            BehaviorCaptureControlStore.finishMissing(nowMillis)
            captureState = WorldGeoBehaviorCaptureState.ACTIVE
            warningActive = false
            ImyvmWorldGeo.logger.warn("Behavior stats capture resumed after pending storage recovered below the 75% watermark.")
        } else if (belowWarningThreshold()) {
            warningActive = false
        }
    }

    internal fun exchangePendingForCheckpoint(): BehaviorStatsCheckpointBatch {
        sessionWorldRoot ?: error("Behavior stats store session is not active")
        val batch = BehaviorStatsCheckpointBatch(
            counts.toMap(),
            dirtySequenceStart,
            dirtySequenceEnd,
            Math.subtractExact(nextWriteSequence, 1L)
        )
        counts.clear()
        dirtySequenceStart = null
        dirtySequenceEnd = 0L
        estimatedPendingBytes = 0L
        return batch
    }

    internal fun restoreCheckpointBatch(batch: BehaviorStatsCheckpointBatch) {
        batch.stats.forEach { (key, count) ->
            counts[key] = Math.addExact(counts[key] ?: 0L, count)
        }
        batch.sequenceStart?.let { start ->
            dirtySequenceStart = minOf(dirtySequenceStart ?: start, start)
            dirtySequenceEnd = maxOf(dirtySequenceEnd, batch.sequenceEnd)
        }
        estimatedPendingBytes = counts.keys.fold(0L) { total, key ->
            Math.addExact(total, estimatedBytes(key))
        }
    }

    internal fun completeCheckpointBatch(nowMillis: Long = System.currentTimeMillis()) {
        if (captureState == WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED && belowRecoveryWatermark()) {
            BehaviorCaptureControlStore.finishMissing(nowMillis)
            captureState = WorldGeoBehaviorCaptureState.ACTIVE
            warningActive = false
        } else if (belowWarningThreshold()) {
            warningActive = false
        }
    }

    internal fun pendingEntryCount(): Int = counts.size

    internal fun readStats(path: Path): Map<BehaviorStatsKey, Long> {
        if (!Files.exists(path)) return emptyMap()
        try {
            val array = JsonParser.parseString(Files.readString(path)).asJsonArray
            val result = linkedMapOf<BehaviorStatsKey, Long>()
            for (element in array) {
                val obj = element.asJsonObject
                val key = BehaviorStatsKey(
                    periodKind = enumValue<NaturalPeriodKind>(obj, "periodKind"),
                    periodId = stringValue(obj, "periodId"),
                    behaviorType = enumValue<WorldGeoBehaviorType>(obj, "behaviorType"),
                    regionId = intValue(obj, "regionId"),
                    scopeId = optionalLongValue(obj, "scopeId"),
                    subSpaceId = optionalLongValue(obj, "subSpaceId"),
                    playerUuid = UUID.fromString(stringValue(obj, "playerUuid")),
                    objectId = optionalStringValue(obj, "objectId"),
                    targetId = optionalStringValue(obj, "targetId")
                )
                val count = longValue(obj, "count")
                validateEntry(key, count)
                result[key] = Math.addExact(result[key] ?: 0L, count)
            }
            return result
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid behavior stats store", error)
        } catch (error: IllegalStateException) {
            throw IOException("Invalid behavior stats store", error)
        } catch (error: NullPointerException) {
            throw IOException("Invalid behavior stats store", error)
        }
    }

    internal fun writeStats(path: Path, stats: Map<BehaviorStatsKey, Long>) {
        val snapshot = stats
        val array = JsonArray()
        var skippedCount = 0
        var firstSkipped: BehaviorStatsKey? = null
        for ((key, count) in snapshot) {
            if (isKeyInvalid(key)) {
                skippedCount++
                if (firstSkipped == null) firstSkipped = key
                continue
            }
            validateEntry(key, count)
            val obj = JsonObject()
            obj.addProperty("periodKind", key.periodKind.name)
            obj.addProperty("periodId", key.periodId)
            obj.addProperty("behaviorType", key.behaviorType.name)
            obj.addProperty("regionId", key.regionId)
            key.scopeId?.let { obj.addProperty("scopeId", it) }
            key.subSpaceId?.let { obj.addProperty("subSpaceId", it) }
            obj.addProperty("playerUuid", key.playerUuid.toString())
            key.objectId?.let { obj.addProperty("objectId", it) }
            key.targetId?.let { obj.addProperty("targetId", it) }
            obj.addProperty("count", count)
            array.add(obj)
        }
        if (skippedCount > 0) {
            val fk = firstSkipped!!
            ImyvmWorldGeo.logger.error(
                "Skipped $skippedCount invalid behavior stats key(s) during save. First: type=${fk.behaviorType} regionId=${fk.regionId} scopeId=${fk.scopeId} subSpaceId=${fk.subSpaceId}"
            )
        }
        RegionDatabase.atomicWrite(path) { output -> output.write(array.toString().toByteArray(Charsets.UTF_8)) }
    }

    internal fun markSessionUnclean() {
        if (sessionWorldRoot != null) cleanCloseAllowed = false
    }

    internal fun abandonSessionForTest() {
        counts.clear()
        BehaviorStatsPageStreamService.closeAllHandles()
        BehaviorStatsCheckpointService.unbindSession()
        SegmentedBehaviorStatsStore.unbindSession()
        BehaviorCaptureControlStore.abandonSession()
        nextWriteSequence = 1L
        dirtySequenceStart = null
        dirtySequenceEnd = 0L
        estimatedPendingBytes = 0L
        captureState = WorldGeoBehaviorCaptureState.ACTIVE
        warningActive = false
        cleanCloseAllowed = true
        sessionWorldRoot = null
    }

    internal fun clearForTest() {
        counts.clear()
        warnedCapacityActions.clear()
        BehaviorStatsPageStreamService.closeAllHandles()
        BehaviorStatsCheckpointService.unbindSession()
        SegmentedBehaviorStatsStore.unbindSession()
        BehaviorCaptureControlStore.resetForTest()
        nextWriteSequence = 1L
        dirtySequenceStart = null
        dirtySequenceEnd = 0L
        estimatedPendingBytes = 0L
        captureState = WorldGeoBehaviorCaptureState.ACTIVE
        warningActive = false
        cleanCloseAllowed = true
        sessionWorldRoot = null
    }

    private fun recordKeys(keys: List<BehaviorStatsKey>, count: Long, eventMillis: Long) {
        if (captureState == WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED) {
            BehaviorCaptureControlStore.noteMissing(eventMillis)
            return
        }
        val updates = linkedMapOf<BehaviorStatsKey, Long>()
        try {
            keys.forEach { key ->
                updates[key] = Math.addExact(counts[key] ?: 0L, count)
            }
        } catch (error: ArithmeticException) {
            warnOnce("overflow", "Dropped a behavior stats event because a counter overflowed.")
            return
        }
        val newKeys = updates.keys.filterNot(counts::containsKey)
        val nextBytes = try {
            newKeys.fold(estimatedPendingBytes) { total, key -> Math.addExact(total, estimatedBytes(key)) }
        } catch (error: ArithmeticException) {
            Long.MAX_VALUE
        }
        if (counts.size + newKeys.size > hardEntryLimit() || nextBytes > hardByteLimit()) {
            captureState = WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED
            warningActive = true
            BehaviorCaptureControlStore.startMissing(eventMillis)
            ImyvmWorldGeo.logger.error(
                "Behavior stats capture suspended at the pending storage hard limit: entries=${counts.size}, estimatedBytes=$estimatedPendingBytes."
            )
            return
        }
        val sequence = nextWriteSequence
        nextWriteSequence = Math.addExact(sequence, 1L)
        if (dirtySequenceStart == null) dirtySequenceStart = sequence
        dirtySequenceEnd = sequence
        updates.forEach { (key, nextCount) -> counts[key] = nextCount }
        estimatedPendingBytes = nextBytes
        if (!warningActive && !belowWarningThreshold()) {
            warningActive = true
            ImyvmWorldGeo.logger.warn(
                "Behavior stats pending storage crossed its warning threshold: entries=${counts.size}, estimatedBytes=$estimatedPendingBytes."
            )
        }
    }

    private fun estimatedBytes(key: BehaviorStatsKey): Long = 96L +
        key.timelineId.toByteArray(Charsets.UTF_8).size +
        key.periodId.toByteArray(Charsets.UTF_8).size +
        key.behaviorType.name.length +
        (key.objectId?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
        (key.targetId?.toByteArray(Charsets.UTF_8)?.size ?: 0)

    private fun hardEntryLimit(): Int = CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.value

    private fun hardByteLimit(): Long = CoreConfig.BEHAVIOR_STATS_MAX_ESTIMATED_BYTES.value.toLong()

    private fun belowWarningThreshold(): Boolean =
        counts.size < CoreConfig.BEHAVIOR_STATS_WARNING_ENTRY_COUNT.value &&
            estimatedPendingBytes < CoreConfig.BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES.value.toLong()

    private fun belowRecoveryWatermark(): Boolean =
        counts.size.toLong() * 4L <= hardEntryLimit().toLong() * 3L &&
            estimatedPendingBytes * 4L <= hardByteLimit() * 3L

    private fun warnOnce(key: String, message: String) {
        if (warnedCapacityActions.add(key)) ImyvmWorldGeo.logger.warn(message)
    }

    private fun errorOnce(key: String, message: String) {
        if (warnedCapacityActions.add(key)) ImyvmWorldGeo.logger.error(message)
    }

    private fun BehaviorStatsKey.matches(query: WorldGeoBehaviorStatsQuery): Boolean =
        periodKind == query.periodKind &&
            periodId == query.periodId &&
            (query.behaviorType == null || behaviorType == query.behaviorType) &&
            (query.regionId == null || regionId == query.regionId) &&
            (query.scopeId == null || scopeId == query.scopeId) &&
            (query.subSpaceId == null || subSpaceId == query.subSpaceId) &&
            (query.playerUuid == null || playerUuid == query.playerUuid) &&
            (query.objectId == null || objectId == query.objectId) &&
            (query.targetId == null || targetId == query.targetId)

    private fun BehaviorStatsKey.toEntry(count: Long): WorldGeoBehaviorStatsEntry = WorldGeoBehaviorStatsEntry(
        periodKind, periodId, behaviorType, regionId, scopeId, subSpaceId, playerUuid, objectId, targetId, count
    )

    private fun validateEntry(key: BehaviorStatsKey, count: Long) {
        require(key.periodId.isNotBlank()) { "period id must not be blank" }
        require(key.regionId > 0) { "region id must be positive" }
        require(key.scopeId == null || key.scopeId != 0L) { "scope id must not be zero" }
        require(key.subSpaceId == null || key.subSpaceId > 0L) { "subspace id must be positive" }
        require(key.objectId == null || key.objectId.isNotBlank()) { "object id must not be blank" }
        require(key.targetId == null || key.targetId.isNotBlank()) { "target id must not be blank" }
        require(count > 0L) { "count must be positive" }
    }

    private fun isKeyInvalid(key: BehaviorStatsKey): Boolean =
        key.periodId.isBlank() ||
        key.regionId <= 0 ||
        key.scopeId == 0L ||
        (key.subSpaceId != null && key.subSpaceId <= 0L) ||
        (key.objectId != null && key.objectId.isBlank()) ||
        (key.targetId != null && key.targetId.isBlank())

    private inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, name: String): T =
        enumValueOf(stringValue(obj, name))

    private fun stringValue(obj: JsonObject, name: String): String = obj.get(name).asString

    private fun optionalStringValue(obj: JsonObject, name: String): String? =
        obj.get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun intValue(obj: JsonObject, name: String): Int = obj.get(name).asInt

    private fun longValue(obj: JsonObject, name: String): Long = obj.get(name).asLong

    private fun optionalLongValue(obj: JsonObject, name: String): Long? =
        obj.get(name)?.takeUnless { it.isJsonNull }?.asLong
}

internal data class BehaviorStatsKey(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val behaviorType: WorldGeoBehaviorType,
    val regionId: Int,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val playerUuid: UUID,
    val objectId: String?,
    val targetId: String? = null,
    val timelineId: String = PeriodTimelineStore.PRODUCTION_TIMELINE_ID
)
