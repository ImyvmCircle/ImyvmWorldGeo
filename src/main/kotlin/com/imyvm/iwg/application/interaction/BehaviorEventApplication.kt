package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.application.event.recordPlayerBehavior
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
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

private fun spaceName(event: WorldGeoBehaviorEvent): String = when {
    event.subSpaceName != null -> "${event.regionName ?: "-"}/${event.scopeName ?: "-"}/${event.subSpaceName}"
    event.scopeName != null -> "${event.regionName ?: "-"}/${event.scopeName}"
    event.regionName != null -> event.regionName
    else -> "-"
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
