package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.time.WorldGeoTimeService
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
