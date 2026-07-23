package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.application.event.recordPlayerBehavior
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsEntry
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoSpaceLevel
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val BEHAVIOR_CMD_BASE = "/imyvmWorldGeo debug behavior"
private val EVENT_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

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
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.header", events.size)!!)
        for (event in events.takeLast(10)) {
            player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.line", event.type.name, event.playerName, spaceName(event), event.x, event.y, event.z, event.objectId ?: "-", event.targetId ?: "-", formatEventTime(event.unixMillis))!!)
        }
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.recent.memory_hint")!!)
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
    val target = currentBehaviorTarget(player) ?: return 0
    if (target.scopeId == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.seed.no_scope")!!)
        return 0
    }
    val pos = player.blockPosition()
    val event = WorldGeoBehaviorEvent(
        type = type,
        playerUuid = player.uuid,
        playerName = player.scoreboardName,
        dimensionId = player.level().dimension().identifier(),
        x = pos.x,
        y = pos.y,
        z = pos.z,
        unixMillis = System.currentTimeMillis(),
        regionId = target.regionId,
        regionName = target.regionName,
        scopeId = target.scopeId,
        scopeName = target.scopeName,
        subSpaceId = target.subSpaceId,
        subSpaceName = target.subSpaceName,
        spaceLevel = if (target.subSpaceId == null) WorldGeoSpaceLevel.SCOPE else WorldGeoSpaceLevel.SUBSPACE,
        objectId = objectId.takeUnless { it == "-" }
    )
    BehaviorStatsStore.recordDebugCount(event, count)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.seed.success", type.name, event.objectId ?: "-", count)!!)
    return 1
}

internal data class BehaviorQueryTarget(
    val regionId: Int,
    val regionName: String,
    val scopeId: Long?,
    val scopeName: String?,
    val subSpaceId: Long?,
    val subSpaceName: String?,
    val wildcardScope: Boolean = false,
    val wildcardSubSpace: Boolean = false
) {
    fun scopeDisplayName(): String = when {
        wildcardScope -> "*"
        scopeName != null -> scopeName
        else -> "-"
    }

    fun subSpaceDisplayName(): String = when {
        wildcardSubSpace -> "*"
        subSpaceName != null -> subSpaceName
        else -> "-"
    }
}

internal fun behaviorTargetForRegion(region: Region): BehaviorQueryTarget = BehaviorQueryTarget(
    regionId = region.numberID,
    regionName = region.name,
    scopeId = null,
    scopeName = null,
    subSpaceId = null,
    subSpaceName = null,
    wildcardScope = true,
    wildcardSubSpace = true
)

internal fun behaviorTargetForScope(region: Region, scope: GeoScope): BehaviorQueryTarget = BehaviorQueryTarget(
    regionId = region.numberID,
    regionName = region.name,
    scopeId = scope.assignedScopeIdOrNull?.raw,
    scopeName = scope.scopeName,
    subSpaceId = null,
    subSpaceName = null,
    wildcardSubSpace = true
)

internal fun behaviorTargetForSubSpace(region: Region, scope: GeoScope, subSpace: SubSpace): BehaviorQueryTarget = BehaviorQueryTarget(
    regionId = region.numberID,
    regionName = region.name,
    scopeId = scope.assignedScopeIdOrNull?.raw,
    scopeName = scope.scopeName,
    subSpaceId = subSpace.subSpaceId,
    subSpaceName = subSpace.name
)

internal fun formatEventTime(unixMillis: Long): String =
    Instant.ofEpochMilli(unixMillis).atZone(WorldGeoTimeService.DEFAULT_ZONE).format(EVENT_TIME_FMT)

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
    val target = currentBehaviorTarget(player) ?: return 0
    return onDebugBehaviorTypedStats(player, NaturalPeriodKind.HOUR.name, periodId, target,
        "$BEHAVIOR_CMD_BASE typedStats")
}

internal fun onDebugBehaviorTypedStats(player: ServerPlayer, target: BehaviorQueryTarget, detailCommandBase: String? = null): Int {
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    return onDebugBehaviorTypedStats(player, NaturalPeriodKind.HOUR.name, periodId, target, detailCommandBase)
}

fun onDebugBehaviorTypedStats(player: ServerPlayer, periodKindName: String, periodId: String): Int {
    val target = currentBehaviorTarget(player) ?: return 0
    return onDebugBehaviorTypedStats(player, periodKindName, periodId, target,
        "$BEHAVIOR_CMD_BASE typedStats $periodKindName \"$periodId\"")
}

internal fun onDebugBehaviorTypedStats(player: ServerPlayer, periodKindName: String, periodId: String, target: BehaviorQueryTarget, detailCommandBase: String? = null): Int {
    val periodKind = parsePeriodKind(player, periodKindName) ?: return 0
    val entries = queryBehaviorEntries(periodKind, periodId, target)
    val block = summarizeBlock(entries)
    val residence = summarizeResidence(entries)
    val combat = summarizeCombat(entries)
    val online = summarizeOnline(entries)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.header", periodKind.name, periodId, target.regionName, target.scopeDisplayName(), target.subSpaceDisplayName())!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.block", block.placedCount, block.brokenCount, block.netDelta, block.playerCount)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.residence", residence.totalResidenceMillis, residence.averageResidenceMillis, residence.chunkCount)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.combat", combat.damageCount, combat.killCount, combat.deathCount, combat.damagedCount, combat.playerCount, combat.targetCount)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.online", online.totalOnlineMillis, online.totalAfkMillis, online.totalNonAfkMillis, online.playerCount)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.keys", ONLINE_OBJECT_ID, AFK_OBJECT_ID, "$RESIDENCE_CHUNK_PREFIX<x,z>", DAMAGED_OBJECT_ID)!!)
    val hintComponent = Translator.tr("interaction.meta.debug.behavior.typed.detail_hint", periodKind.name, periodId)!!
    if (detailCommandBase != null) {
        player.sendSystemMessage(hintComponent.copy().withStyle { it.withClickEvent(ClickEvent.RunCommand("$detailCommandBase detail")) })
    } else {
        player.sendSystemMessage(hintComponent)
    }
    return 1
}

fun onDebugBehaviorTypedStatsDetail(player: ServerPlayer, periodKindName: String?, periodId: String?, pageRaw: String?, pageCommandBase: String? = null): Int {
    val target = currentBehaviorTarget(player) ?: return 0
    return onDebugBehaviorTypedStatsDetail(player, periodKindName, periodId, pageRaw, target, pageCommandBase)
}

internal fun onDebugBehaviorTypedStatsDetail(player: ServerPlayer, periodKindName: String?, periodId: String?, pageRaw: String?, target: BehaviorQueryTarget, pageCommandBase: String? = null): Int {
    val ids = WorldGeoTimeService.currentNaturalPeriodIds()
    val kindName = periodKindName ?: NaturalPeriodKind.HOUR.name
    val kind = parsePeriodKind(player, kindName) ?: return 0
    val resolvedPeriodId = periodId ?: ids[kind] ?: return 0
    val entries = queryBehaviorEntries(kind, resolvedPeriodId, target)
    val lines = buildTypedDetailLines(entries)
    sendBehaviorPagedComponents(
        player,
        "interaction.meta.debug.behavior.typed.detail.header",
        "interaction.meta.debug.behavior.detail.empty",
        lines,
        pageRaw,
        kind.name,
        resolvedPeriodId,
        target,
        pageCommandBase
    )
    return 1
}

fun onDebugBehaviorStats(player: ServerPlayer): Int {
    val target = currentBehaviorTarget(player) ?: return 1
    return onDebugBehaviorStats(player, target)
}

internal fun onDebugBehaviorStats(player: ServerPlayer, target: BehaviorQueryTarget): Int {
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    val entries = queryBehaviorEntries(NaturalPeriodKind.HOUR, periodId, target)
    val total = entries.sumOf { it.count }
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.stats.header",
            periodId,
            target.regionName,
            target.scopeDisplayName(),
            target.subSpaceDisplayName(),
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

fun onDebugBehaviorStatsDetail(player: ServerPlayer, pageRaw: String?, pageCommandBase: String? = null): Int {
    val target = currentBehaviorTarget(player) ?: return 1
    return onDebugBehaviorStatsDetail(player, pageRaw, target, pageCommandBase)
}

internal fun onDebugBehaviorStatsDetail(player: ServerPlayer, pageRaw: String?, target: BehaviorQueryTarget, pageCommandBase: String? = null): Int {
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    val entries = queryBehaviorEntries(NaturalPeriodKind.HOUR, periodId, target)
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
    sendBehaviorPagedComponents(
        player,
        "interaction.meta.debug.behavior.stats.detail.header",
        "interaction.meta.debug.behavior.detail.empty",
        lines,
        pageRaw,
        NaturalPeriodKind.HOUR.name,
        periodId,
        target,
        pageCommandBase
    )
    return 1
}

internal fun currentBehaviorTarget(player: ServerPlayer): BehaviorQueryTarget? {
    val pos = player.blockPosition()
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z)
    if (resolved == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.no_space")!!)
        return null
    }
    return if (resolved.third == null) {
        behaviorTargetForScope(resolved.first, resolved.second)
    } else {
        behaviorTargetForSubSpace(resolved.first, resolved.second, resolved.third!!)
    }
}

private fun parsePeriodKind(player: ServerPlayer, periodKindName: String): NaturalPeriodKind? =
    runCatching { enumValueOf<NaturalPeriodKind>(periodKindName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.invalid_kind", periodKindName)!!)
        null
    }

internal fun queryBehaviorEntries(
    periodKind: NaturalPeriodKind,
    periodId: String,
    target: BehaviorQueryTarget
): List<WorldGeoBehaviorStatsEntry> = BehaviorStatsStore.query(
    WorldGeoBehaviorStatsQuery(
        periodKind = periodKind,
        periodId = periodId,
        regionId = target.regionId,
        scopeId = target.scopeId,
        subSpaceId = target.subSpaceId
    )
).filter { !target.wildcardScope || it.scopeId != null }

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
                val chunkId = chunk.removePrefix(RESIDENCE_CHUNK_PREFIX)
                residence[chunkId] = (residence[chunkId] ?: 0L) + entry.count
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

private fun sendBehaviorPagedComponents(
    player: ServerPlayer,
    headerKey: String,
    emptyKey: String,
    lines: List<Component>,
    pageRaw: String?,
    periodKind: String,
    periodId: String,
    target: BehaviorQueryTarget,
    pageCommandBase: String? = null
) {
    if (lines.isEmpty()) {
        player.sendSystemMessage(Translator.tr(emptyKey, periodKind, periodId, target.regionName, target.scopeDisplayName(), target.subSpaceDisplayName())!!)
        return
    }
    val totalPages = ((lines.size + DEBUG_DETAIL_PAGE_SIZE - 1) / DEBUG_DETAIL_PAGE_SIZE).coerceAtLeast(1)
    val page = (pageRaw?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
    player.sendSystemMessage(
        Translator.tr(
            headerKey,
            periodKind,
            periodId,
            target.regionName,
            target.scopeDisplayName(),
            target.subSpaceDisplayName(),
            page,
            totalPages,
            lines.size
        )!!
    )
    if (pageCommandBase != null && totalPages > 1) {
        player.sendSystemMessage(buildPageNavComponent(page, totalPages, pageCommandBase))
    }
    lines.drop((page - 1) * DEBUG_DETAIL_PAGE_SIZE).take(DEBUG_DETAIL_PAGE_SIZE).forEach(player::sendSystemMessage)
}

private fun buildPageNavComponent(page: Int, totalPages: Int, commandBase: String): Component {
    val nav = Component.empty()
    if (page > 1) {
        nav.append(
            Translator.tr("interaction.meta.debug.behavior.page.prev")!!
                .copy()
                .withStyle { it
                    .withColor(TextColor.fromLegacyFormat(ChatFormatting.AQUA))
                    .withClickEvent(ClickEvent.RunCommand("$commandBase ${page - 1}"))
                    .withHoverEvent(HoverEvent.ShowText(Translator.tr("interaction.meta.debug.behavior.page.prev.hover", page - 1)!!))
                }
        )
        nav.append(Component.literal(" "))
    }
    if (page < totalPages) {
        nav.append(
            Translator.tr("interaction.meta.debug.behavior.page.next")!!
                .copy()
                .withStyle { it
                    .withColor(TextColor.fromLegacyFormat(ChatFormatting.AQUA))
                    .withClickEvent(ClickEvent.RunCommand("$commandBase ${page + 1}"))
                    .withHoverEvent(HoverEvent.ShowText(Translator.tr("interaction.meta.debug.behavior.page.next.hover", page + 1)!!))
                }
        )
    }
    return nav
}

private fun summarizeBlock(entries: List<WorldGeoBehaviorStatsEntry>): BlockSummary {
    val contributions = linkedMapOf<UUID, Long>()
    var placedCount = 0L
    var brokenCount = 0L
    entries.forEach { entry ->
        when (entry.behaviorType) {
            WorldGeoBehaviorType.BLOCK_PLACE -> {
                placedCount += entry.count
                contributions[entry.playerUuid] = (contributions[entry.playerUuid] ?: 0L) + entry.count
            }
            WorldGeoBehaviorType.BLOCK_BREAK -> {
                brokenCount += entry.count
                contributions[entry.playerUuid] = (contributions[entry.playerUuid] ?: 0L) - entry.count
            }
            else -> Unit
        }
    }
    return BlockSummary(placedCount, brokenCount, placedCount - brokenCount, contributions.size)
}

private fun summarizeResidence(entries: List<WorldGeoBehaviorStatsEntry>): ResidenceSummary {
    val chunks = linkedMapOf<String, Long>()
    entries.asSequence()
        .filter { it.behaviorType == WorldGeoBehaviorType.SPACE_ENTER }
        .forEach { entry ->
            val chunkId = entry.objectId?.takeIf { it.startsWith(RESIDENCE_CHUNK_PREFIX) }?.removePrefix(RESIDENCE_CHUNK_PREFIX) ?: return@forEach
            chunks[chunkId] = (chunks[chunkId] ?: 0L) + entry.count
        }
    val total = chunks.values.sum()
    return ResidenceSummary(total, if (chunks.isEmpty()) 0L else total / chunks.size, chunks.size)
}

private fun summarizeCombat(entries: List<WorldGeoBehaviorStatsEntry>): CombatSummary {
    val players = linkedMapOf<UUID, LongArray>()
    val targets = linkedMapOf<String, LongArray>()
    entries.forEach { entry ->
        when (entry.behaviorType) {
            WorldGeoBehaviorType.ENTITY_DAMAGE -> {
                if (entry.objectId == DAMAGED_OBJECT_ID) {
                    players.getOrPut(entry.playerUuid) { LongArray(4) }[3] += entry.count
                } else {
                    players.getOrPut(entry.playerUuid) { LongArray(4) }[0] += entry.count
                    entry.targetId?.let { targets.getOrPut(it) { LongArray(2) }[0] += entry.count }
                }
            }
            WorldGeoBehaviorType.ENTITY_KILL -> {
                players.getOrPut(entry.playerUuid) { LongArray(4) }[1] += entry.count
                entry.targetId?.let { targets.getOrPut(it) { LongArray(2) }[1] += entry.count }
            }
            WorldGeoBehaviorType.PLAYER_DEATH -> players.getOrPut(entry.playerUuid) { LongArray(4) }[2] += entry.count
            else -> Unit
        }
    }
    return CombatSummary(
        damageCount = players.values.sumOf { it[0] },
        killCount = players.values.sumOf { it[1] },
        deathCount = players.values.sumOf { it[2] },
        damagedCount = players.values.sumOf { it[3] },
        playerCount = players.size,
        targetCount = targets.size
    )
}

private fun summarizeOnline(entries: List<WorldGeoBehaviorStatsEntry>): OnlineSummary {
    val players = linkedMapOf<UUID, LongArray>()
    entries.asSequence()
        .filter { it.behaviorType == WorldGeoBehaviorType.ITEM_USE }
        .forEach { entry ->
            when (entry.objectId) {
                ONLINE_OBJECT_ID -> players.getOrPut(entry.playerUuid) { LongArray(2) }[0] += entry.count
                AFK_OBJECT_ID -> players.getOrPut(entry.playerUuid) { LongArray(2) }[1] += entry.count
                else -> Unit
            }
        }
    val totalOnline = players.values.sumOf { it[0] }
    val totalAfk = players.values.sumOf { it[1] }
    return OnlineSummary(totalOnline, totalAfk, (totalOnline - totalAfk).coerceAtLeast(0L), players.size)
}

private data class BlockDetailKey(val playerUuid: UUID, val objectId: String)
private data class CombatDetailKey(val playerUuid: UUID, val objectId: String, val targetId: String)
private data class BlockSummary(val placedCount: Long, val brokenCount: Long, val netDelta: Long, val playerCount: Int)
private data class ResidenceSummary(val totalResidenceMillis: Long, val averageResidenceMillis: Long, val chunkCount: Int)
private data class CombatSummary(
    val damageCount: Long,
    val killCount: Long,
    val deathCount: Long,
    val damagedCount: Long,
    val playerCount: Int,
    val targetCount: Int
)
private data class OnlineSummary(val totalOnlineMillis: Long, val totalAfkMillis: Long, val totalNonAfkMillis: Long, val playerCount: Int)
