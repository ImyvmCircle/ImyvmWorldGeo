package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.time.WorldGeoTimeService
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object BehaviorStatsStore {
    private const val FILE_NAME = "iwg_behavior_stats.json"
    private const val MAX_ENTRY_COUNT = 100_000
    private const val RESIDENCE_CHUNK_PREFIX = "residence_chunk:"
    private const val ONLINE_OBJECT_ID = "online_millis"
    private const val AFK_OBJECT_ID = "afk_millis"
    private const val DAMAGED_OBJECT_ID = "damaged"
    private var sessionWorldRoot: Path? = null
    private val counts = linkedMapOf<BehaviorStatsKey, Long>()

    internal fun bindSession(worldRoot: Path) {
        check(sessionWorldRoot == null) { "Behavior stats store session is already active" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        counts.clear()
        counts.putAll(readStats(root.resolve(FILE_NAME)))
        sessionWorldRoot = root
    }

    internal fun unbindSession() {
        counts.clear()
        sessionWorldRoot = null
    }

    fun record(event: WorldGeoBehaviorEvent) {
        record(event, 1L)
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
        val periodIds = WorldGeoTimeService.currentNaturalPeriodIds(Clock.fixed(Instant.ofEpochMilli(event.unixMillis), ZoneOffset.UTC))
        for ((periodKind, periodId) in periodIds) {
            val key = BehaviorStatsKey(
                periodKind = periodKind,
                periodId = periodId,
                behaviorType = event.type,
                regionId = regionId,
                scopeId = event.scopeId,
                subSpaceId = event.subSpaceId,
                playerUuid = event.playerUuid,
                objectId = event.objectId,
                targetId = event.targetId
            )
            counts[key] = Math.addExact(counts[key] ?: 0L, count)
            require(counts.size <= MAX_ENTRY_COUNT) { "behavior stats entry count exceeds $MAX_ENTRY_COUNT" }
        }
    }

    fun query(query: WorldGeoBehaviorStatsQuery): List<WorldGeoBehaviorStatsEntry> {
        require(query.periodId.isNotBlank()) { "period id must not be blank" }
        return counts.asSequence()
            .filter { (key, _) -> key.matches(query) }
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

    internal fun save() {
        val root = sessionWorldRoot ?: error("Behavior stats store session is not active")
        writeStats(root.resolve(FILE_NAME), counts)
    }

    internal fun readStats(path: Path): Map<BehaviorStatsKey, Long> {
        if (!Files.exists(path)) return emptyMap()
        try {
            val array = JsonParser.parseString(Files.readString(path)).asJsonArray
            require(array.size() <= MAX_ENTRY_COUNT) { "too many behavior stats entries" }
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
        require(stats.size <= MAX_ENTRY_COUNT) { "too many behavior stats entries" }
        val array = JsonArray()
        for ((key, count) in stats) {
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
        RegionDatabase.atomicWrite(path) { output -> output.write(array.toString().toByteArray(Charsets.UTF_8)) }
    }

    internal fun clearForTest() {
        counts.clear()
        sessionWorldRoot = null
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
        require(key.scopeId == null || key.scopeId > 0L) { "scope id must be positive" }
        require(key.subSpaceId == null || key.subSpaceId > 0L) { "subspace id must be positive" }
        require(key.objectId == null || key.objectId.isNotBlank()) { "object id must not be blank" }
        require(key.targetId == null || key.targetId.isNotBlank()) { "target id must not be blank" }
        require(count > 0L) { "count must be positive" }
    }

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
    val targetId: String? = null
)
