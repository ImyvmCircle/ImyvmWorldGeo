package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.application.event.recordPlayerBehavior
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsEntry
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoSpaceLevel
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

fun onDebugBehaviorEmit(player: ServerPlayer): Int {
    val pos = player.blockPosition()
    recordPlayerBehavior(WorldGeoBehaviorType.DEBUG_TEST, player, player.level(), pos, objectId = "debug")
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.emit", player.scoreboardName, pos.x, pos.y, pos.z)!!)
    return 1
}

fun onDebugBehaviorRecent(player: ServerPlayer): Int {
    val events = WorldGeoBehaviorEventBus.getRecentEvents()
    if (events.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.none")!!)
        return 1
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.header", events.size)!!)
    for (event in events.takeLast(10)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.line", event.type.name, event.playerName, spaceName(event), event.x, event.y, event.z, event.objectId ?: "-", event.targetId ?: "-")!!)
    }
    return 1
}

fun onDebugBehaviorSeed(player: ServerPlayer, typeName: String, objectId: String, countRaw: String): Int {
    val type = runCatching { enumValueOf<WorldGeoBehaviorType>(typeName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.seed.invalid_type", typeName)!!)
        return 0
    }
    val count = countRaw.toLongOrNull()?.takeIf { it > 0L } ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.seed.invalid_count", countRaw)!!)
        return 0
    }
    val pos = player.blockPosition()
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z) ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.no_space")!!)
        return 0
    }
    val event = WorldGeoBehaviorEvent(
        type = type,
        playerUuid = player.uuid,
        playerName = player.scoreboardName,
        dimensionId = player.level().dimension().identifier(),
        x = pos.x,
        y = pos.y,
        z = pos.z,
        unixMillis = System.currentTimeMillis(),
        regionId = resolved.first.numberID,
        regionName = resolved.first.name,
        scopeId = resolved.second.assignedScopeIdOrNull?.raw,
        scopeName = resolved.second.scopeName,
        subSpaceId = resolved.third?.subSpaceId,
        subSpaceName = resolved.third?.name,
        spaceLevel = if (resolved.third == null) WorldGeoSpaceLevel.SCOPE else WorldGeoSpaceLevel.SUBSPACE,
        objectId = objectId.takeUnless { it == "-" }
    )
    BehaviorStatsStore.recordDebugCount(event, count)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.seed.success", type.name, event.objectId ?: "-", count)!!)
    return 1
}

private fun spaceName(event: WorldGeoBehaviorEvent): String = when {
    event.subSpaceName != null -> "${event.regionName ?: "-"}/${event.scopeName ?: "-"}/${event.subSpaceName}"
    event.scopeName != null -> "${event.regionName ?: "-"}/${event.scopeName}"
    event.regionName != null -> event.regionName
    else -> "-"
}

private const val DEBUG_DETAIL_PAGE_SIZE = 5
private const val RESIDENCE_CHUNK_PREFIX = "residence_chunk:"
private const val ONLINE_OBJECT_ID = "online_millis"
private const val AFK_OBJECT_ID = "afk_millis"
private const val DAMAGED_OBJECT_ID = "damaged"

fun onDebugBehaviorTypedStats(player: ServerPlayer): Int {
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    return onDebugBehaviorTypedStats(player, NaturalPeriodKind.HOUR.name, periodId)
}

fun onDebugBehaviorTypedStats(player: ServerPlayer, periodKindName: String, periodId: String): Int {
    val periodKind = parsePeriodKind(player, periodKindName) ?: return 0
    val target = currentBehaviorTarget(player) ?: return 0
    val block = BehaviorStatsStore.queryBlockDelta(periodKind, periodId, target.regionId, target.scopeId, target.subSpaceId, null)
    val residence = BehaviorStatsStore.queryResidence(periodKind, periodId, target.regionId, target.scopeId, target.subSpaceId)
    val combat = BehaviorStatsStore.queryEntityCombat(periodKind, periodId, target.regionId, target.scopeId, target.subSpaceId, null)
    val online = BehaviorStatsStore.queryOnlineTime(periodKind, periodId, target.regionId, target.scopeId, target.subSpaceId, null)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.header", periodKind.name, periodId, target.regionName, target.scopeName, target.subSpaceName)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.block", block.placedCount, block.brokenCount, block.netDelta, block.playerContributions.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.residence", residence.totalResidenceMillis, residence.averageResidenceMillis, residence.chunkResidenceMillis.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.combat", combat.damageCount, combat.killCount, combat.deathCount, combat.damagedCount, combat.playerStats.size, combat.targetStats.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.online", online.totalOnlineMillis, online.totalAfkMillis, online.totalNonAfkMillis, online.playerStats.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.detail_hint", periodKind.name, periodId)!!)
    return 1
}

fun onDebugBehaviorTypedStatsDetail(player: ServerPlayer, periodKindName: String?, periodId: String?, pageRaw: String?): Int {
    val ids = WorldGeoTimeService.currentNaturalPeriodIds()
    val kindName = periodKindName ?: NaturalPeriodKind.HOUR.name
    val kind = parsePeriodKind(player, kindName) ?: return 0
    val resolvedPeriodId = periodId ?: ids[kind] ?: return 0
    val target = currentBehaviorTarget(player) ?: return 0
    val entries = queryCurrentSpaceEntries(kind, resolvedPeriodId, target)
    val lines = buildTypedDetailLines(entries)
    sendPagedComponents(player, "interaction.meta.debug.behavior.typed.detail.header", lines, pageRaw, kind.name, resolvedPeriodId)
    return 1
}

fun onDebugBehaviorStats(player: ServerPlayer): Int {
    val target = currentBehaviorTarget(player) ?: return 1
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    val entries = queryCurrentSpaceEntries(NaturalPeriodKind.HOUR, periodId, target)
    val total = entries.sumOf { it.count }
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.stats.header",
            periodId,
            target.regionName,
            target.scopeName,
            target.subSpaceName,
            total
        )!!
    )
    entries.groupBy { it.behaviorType }
        .mapValues { (_, values) -> values.sumOf { it.count } }
        .toSortedMap(compareBy { it.name })
        .forEach { (type, count) ->
            player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.line", type.name, count)!!)
        }
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.detail_hint")!!)
    return 1
}

fun onDebugBehaviorStatsDetail(player: ServerPlayer, pageRaw: String?): Int {
    val target = currentBehaviorTarget(player) ?: return 1
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    val entries = queryCurrentSpaceEntries(NaturalPeriodKind.HOUR, periodId, target)
        .sortedWith(compareBy<WorldGeoBehaviorStatsEntry> { it.behaviorType.name }
            .thenBy { it.playerUuid.toString() }
            .thenBy { it.objectId ?: "" }
            .thenBy { it.targetId ?: "" })
    val lines = entries.map {
        Translator.tr(
            "interaction.meta.debug.behavior.stats.detail.line",
            it.behaviorType.name,
            it.playerUuid,
            it.objectId ?: "-",
            it.targetId ?: "-",
            it.count
        )!!
    }
    sendPagedComponents(player, "interaction.meta.debug.behavior.stats.detail.header", lines, pageRaw, NaturalPeriodKind.HOUR.name, periodId)
    return 1
}

private data class CurrentBehaviorTarget(
    val regionId: Int,
    val regionName: String,
    val scopeId: Long?,
    val scopeName: String,
    val subSpaceId: Long?,
    val subSpaceName: String
)

private data class BlockDetailKey(val playerUuid: UUID, val objectId: String)
private data class CombatDetailKey(val playerUuid: UUID, val objectId: String, val targetId: String)

private fun currentBehaviorTarget(player: ServerPlayer): CurrentBehaviorTarget? {
    val pos = player.blockPosition()
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z)
    if (resolved == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.no_space")!!)
        return null
    }
    return CurrentBehaviorTarget(
        resolved.first.numberID,
        resolved.first.name,
        resolved.second.assignedScopeIdOrNull?.raw,
        resolved.second.scopeName,
        resolved.third?.subSpaceId,
        resolved.third?.name ?: "-"
    )
}

private fun parsePeriodKind(player: ServerPlayer, periodKindName: String): NaturalPeriodKind? =
    runCatching { enumValueOf<NaturalPeriodKind>(periodKindName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.invalid_kind", periodKindName)!!)
        null
    }

private fun queryCurrentSpaceEntries(
    periodKind: NaturalPeriodKind,
    periodId: String,
    target: CurrentBehaviorTarget
): List<WorldGeoBehaviorStatsEntry> = BehaviorStatsStore.query(
    WorldGeoBehaviorStatsQuery(
        periodKind = periodKind,
        periodId = periodId,
        regionId = target.regionId,
        scopeId = target.scopeId,
        subSpaceId = target.subSpaceId
    )
)

private fun buildTypedDetailLines(entries: List<WorldGeoBehaviorStatsEntry>): List<Component> {
    val lines = mutableListOf<Component>()
    val block = linkedMapOf<BlockDetailKey, LongArray>()
    val combat = linkedMapOf<CombatDetailKey, LongArray>()
    val residence = linkedMapOf<String, Long>()
    val online = linkedMapOf<UUID, LongArray>()

    entries.forEach { entry ->
        when (entry.behaviorType) {
            WorldGeoBehaviorType.BLOCK_PLACE -> block.getOrPut(BlockDetailKey(entry.playerUuid, entry.objectId ?: "-")) { LongArray(2) }[0] += entry.count
            WorldGeoBehaviorType.BLOCK_BREAK -> block.getOrPut(BlockDetailKey(entry.playerUuid, entry.objectId ?: "-")) { LongArray(2) }[1] += entry.count
            WorldGeoBehaviorType.ENTITY_DAMAGE -> {
                if (entry.objectId == DAMAGED_OBJECT_ID) {
                    combat.getOrPut(CombatDetailKey(entry.playerUuid, "-", entry.targetId ?: "-")) { LongArray(4) }[3] += entry.count
                } else {
                    combat.getOrPut(CombatDetailKey(entry.playerUuid, entry.objectId ?: "-", entry.targetId ?: "-")) { LongArray(4) }[0] += entry.count
                }
            }
            WorldGeoBehaviorType.ENTITY_KILL -> combat.getOrPut(CombatDetailKey(entry.playerUuid, entry.objectId ?: "-", entry.targetId ?: "-")) { LongArray(4) }[1] += entry.count
            WorldGeoBehaviorType.PLAYER_DEATH -> combat.getOrPut(CombatDetailKey(entry.playerUuid, entry.objectId ?: "-", entry.targetId ?: "-")) { LongArray(4) }[2] += entry.count
            WorldGeoBehaviorType.SPACE_ENTER -> entry.objectId?.takeIf { it.startsWith(RESIDENCE_CHUNK_PREFIX) }?.let { chunk ->
                residence[chunk.removePrefix(RESIDENCE_CHUNK_PREFIX)] = (residence[chunk.removePrefix(RESIDENCE_CHUNK_PREFIX)] ?: 0L) + entry.count
            }
            WorldGeoBehaviorType.ITEM_USE -> when (entry.objectId) {
                ONLINE_OBJECT_ID -> online.getOrPut(entry.playerUuid) { LongArray(2) }[0] += entry.count
                AFK_OBJECT_ID -> online.getOrPut(entry.playerUuid) { LongArray(2) }[1] += entry.count
                else -> Unit
            }
            else -> Unit
        }
    }

    block.entries.sortedWith(compareBy({ it.key.playerUuid.toString() }, { it.key.objectId })).forEach { (key, values) ->
        lines.add(Translator.tr("interaction.meta.debug.behavior.typed.detail.block", key.playerUuid, key.objectId, values[0], values[1], values[0] - values[1])!!)
    }
    combat.entries.sortedWith(compareBy({ it.key.playerUuid.toString() }, { it.key.objectId }, { it.key.targetId })).forEach { (key, values) ->
        lines.add(Translator.tr("interaction.meta.debug.behavior.typed.detail.combat", key.playerUuid, key.objectId, key.targetId, values[0], values[1], values[2], values[3])!!)
    }
    residence.entries.sortedByDescending { it.value }.forEach { (chunk, millis) ->
        lines.add(Translator.tr("interaction.meta.debug.behavior.typed.detail.residence", chunk, millis)!!)
    }
    online.entries.sortedBy { it.key.toString() }.forEach { (playerUuid, values) ->
        val nonAfk = (values[0] - values[1]).coerceAtLeast(0L)
        lines.add(Translator.tr("interaction.meta.debug.behavior.typed.detail.online", playerUuid, values[0], values[1], nonAfk)!!)
    }
    return lines
}

private fun sendPagedComponents(player: ServerPlayer, headerKey: String, lines: List<Component>, pageRaw: String?, periodKind: String, periodId: String) {
    if (lines.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.detail.empty", periodKind, periodId)!!)
        return
    }
    val totalPages = ((lines.size + DEBUG_DETAIL_PAGE_SIZE - 1) / DEBUG_DETAIL_PAGE_SIZE).coerceAtLeast(1)
    val page = (pageRaw?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
    player.sendSystemMessage(Translator.tr(headerKey, periodKind, periodId, page, totalPages, lines.size)!!)
    lines.drop((page - 1) * DEBUG_DETAIL_PAGE_SIZE).take(DEBUG_DETAIL_PAGE_SIZE).forEach(player::sendSystemMessage)
}
