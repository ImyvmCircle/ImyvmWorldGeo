package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.time.WorldGeoPeriodTracker
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.application.time.WorldGeoTestPeriodTracker
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onDebugTime(player: ServerPlayer): Int {
    val snapshot = WorldGeoTimeService.snapshot(player.level())
    val game = snapshot.game
    val real = snapshot.real
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.time.header", game.dimensionId.toString())!!)
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.time.game",
            game.gameTick,
            game.dayTimeTick,
            game.gameDay,
            game.dayTick,
            game.raining,
            game.thundering,
            game.moonPhase
        )!!
    )
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.time.real",
            real.unixSeconds,
            real.zoneId,
            real.localDateTime.toString(),
            real.naturalHour,
            real.naturalDay,
            real.naturalWeek,
            real.naturalMonth,
            real.naturalYear
        )!!
    )
    return 1
}

fun onDebugPeriodEmit(player: ServerPlayer, periodKindName: String, previousId: String, currentId: String): Int {
    val periodKind = runCatching { enumValueOf<NaturalPeriodKind>(periodKindName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.invalid_kind", periodKindName)!!)
        return 0
    }
    val count = WorldGeoPeriodTracker.emitMissedForDebug(periodKind, previousId, currentId)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.emit", periodKind.name, previousId, currentId, count)!!)
    return count
}


fun onDebugTestPeriodStatus(player: ServerPlayer): Int {
    val ids = WorldGeoTestPeriodTracker.status()
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.test_period.status",
            ids[NaturalPeriodKind.HOUR] ?: "-",
            ids[NaturalPeriodKind.DAY] ?: "-",
            ids[NaturalPeriodKind.WEEK] ?: "-",
            ids[NaturalPeriodKind.MONTH] ?: "-"
        )!!
    )
    return 1
}

fun onDebugTestPeriodReset(player: ServerPlayer): Int {
    WorldGeoTestPeriodTracker.reset()
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.test_period.reset")!!)
    return 1
}

fun onDebugTestPeriodTick(player: ServerPlayer): Int {
    val count = WorldGeoTestPeriodTracker.process()
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.test_period.tick", count)!!)
    return count
}

fun onDebugTestPeriodEmit(player: ServerPlayer, periodKindName: String, previousId: String, currentId: String): Int {
    val periodKind = runCatching { enumValueOf<NaturalPeriodKind>(periodKindName.uppercase()) }.getOrNull() ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.period.invalid_kind", periodKindName)!!)
        return 0
    }
    val count = WorldGeoTestPeriodTracker.emitMissedForDebug(periodKind, previousId, currentId)
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.test_period.emit", periodKind.name, previousId, currentId, count)!!)
    return count
}
