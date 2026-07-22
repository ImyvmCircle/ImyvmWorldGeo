package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.time.WorldGeoPeriodTracker
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.application.time.TestPeriodModeService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.config.TestPeriodConfig
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



fun onDebugTestPeriodStart(player: ServerPlayer, weekCountRaw: String?): Int {
    val weekCount = weekCountRaw?.toIntOrNull() ?: TestPeriodModeService.DEFAULT_WEEK_COUNT
    if (weekCount <= 0) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.test_period.invalid_weeks", weekCountRaw ?: "")!!)
        return 0
    }
    val status = TestPeriodModeService.start(weekCount)
    sendTestPeriodStatus(player, "interaction.meta.debug.test_period.started", status)
    return 1
}

fun onDebugTestPeriodStop(player: ServerPlayer): Int {
    TestPeriodModeService.stop()
    WorldGeoPeriodTracker.resumeNaturalWithoutBackfill()
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.test_period.stopped")!!)
    return 1
}

fun onDebugTestPeriodStatus(player: ServerPlayer): Int {
    val status = TestPeriodModeService.status()
    if (!status.active) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.debug.test_period.inactive", status.weekLengthSeconds, TestPeriodModeService.DEFAULT_WEEK_COUNT)!!
        )
        return 0
    }
    sendTestPeriodStatus(player, "interaction.meta.debug.test_period.status", status)
    return 1
}

private fun sendTestPeriodStatus(player: ServerPlayer, key: String, status: com.imyvm.iwg.application.time.TestPeriodModeStatus) {
    val weekSeconds = status.weekLengthSeconds
    val daySeconds = (weekSeconds / 7).coerceAtLeast(1)
    val hourSeconds = (daySeconds / 24).coerceAtLeast(1)
    val monthSeconds = Math.multiplyExact(weekSeconds, 4)
    player.sendSystemMessage(
        Translator.tr(
            key,
            TestPeriodConfig.TEST_WEEK_LENGTH_SECONDS.key,
            weekSeconds,
            TestPeriodModeService.DEFAULT_WEEK_COUNT,
            hourSeconds,
            daySeconds,
            weekSeconds,
            monthSeconds,
            status.startedAtMillis?.let { TestPeriodModeService.formatStartedAt(it, WorldGeoTimeService.DEFAULT_ZONE) } ?: "-",
            TestPeriodModeService.formatDuration(status.remainingMillis),
            status.currentWeek,
            status.weekCount,
            status.periodIds[NaturalPeriodKind.HOUR] ?: "-",
            status.periodIds[NaturalPeriodKind.DAY] ?: "-",
            status.periodIds[NaturalPeriodKind.WEEK] ?: "-",
            status.periodIds[NaturalPeriodKind.MONTH] ?: "-"
        )!!
    )
}
