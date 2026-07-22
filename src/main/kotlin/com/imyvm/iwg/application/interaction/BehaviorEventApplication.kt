package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.application.event.recordPlayerBehavior
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoSpaceLevel
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

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
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.line", event.type.name, event.playerName, spaceName(event), event.x, event.y, event.z, event.objectId ?: "-")!!)
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

fun onDebugBehaviorTypedStats(player: ServerPlayer): Int {
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    return onDebugBehaviorTypedStats(player, NaturalPeriodKind.HOUR.name, periodId)
}

fun onDebugBehaviorTypedStats(player: ServerPlayer, periodKindName: String, periodId: String): Int {
    val periodKind = runCatching { enumValueOf<NaturalPeriodKind>(periodKindName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.invalid_kind", periodKindName)!!)
        return 0
    }
    val pos = player.blockPosition()
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z) ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.no_space")!!)
        return 0
    }
    val regionId = resolved.first.numberID
    val scopeId = resolved.second.assignedScopeIdOrNull?.raw
    val subSpaceId = resolved.third?.subSpaceId
    val block = BehaviorStatsStore.queryBlockDelta(periodKind, periodId, regionId, scopeId, subSpaceId, null)
    val residence = BehaviorStatsStore.queryResidence(periodKind, periodId, regionId, scopeId, subSpaceId)
    val combat = BehaviorStatsStore.queryEntityCombat(periodKind, periodId, regionId, scopeId, subSpaceId, null)
    val online = BehaviorStatsStore.queryOnlineTime(periodKind, periodId, regionId, scopeId, subSpaceId, null)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.header", periodKind.name, periodId, resolved.first.name, resolved.second.scopeName, resolved.third?.name ?: "-")!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.block", block.placedCount, block.brokenCount, block.netDelta, block.playerContributions.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.residence", residence.totalResidenceMillis, residence.averageResidenceMillis, residence.chunkResidenceMillis.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.combat", combat.damageCount, combat.killCount, combat.deathCount, combat.damagedCount, combat.playerStats.size)!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.typed.online", online.totalOnlineMillis, online.totalAfkMillis, online.totalNonAfkMillis, online.playerStats.size)!!)
    return 1
}

fun onDebugBehaviorStats(player: ServerPlayer): Int {
    val pos = player.blockPosition()
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z)
    if (resolved == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.no_space")!!)
        return 1
    }
    val periodId = WorldGeoTimeService.currentNaturalPeriodIds()[NaturalPeriodKind.HOUR] ?: return 0
    val entries = BehaviorStatsStore.query(
        WorldGeoBehaviorStatsQuery(
            periodKind = NaturalPeriodKind.HOUR,
            periodId = periodId,
            regionId = resolved.first.numberID,
            scopeId = resolved.second.assignedScopeIdOrNull?.raw,
            subSpaceId = resolved.third?.subSpaceId
        )
    )
    val total = entries.sumOf { it.count }
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.stats.header",
            periodId,
            resolved.first.name,
            resolved.second.scopeName,
            resolved.third?.name ?: "-",
            total
        )!!
    )
    entries.groupBy { it.behaviorType }
        .mapValues { (_, values) -> values.sumOf { it.count } }
        .toSortedMap(compareBy { it.name })
        .forEach { (type, count) ->
            player.sendSystemMessage(Translator.tr("interaction.meta.debug.behavior.stats.line", type.name, count)!!)
        }
    return 1
}
