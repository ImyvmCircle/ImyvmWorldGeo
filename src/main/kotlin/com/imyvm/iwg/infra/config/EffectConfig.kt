package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

private const val TICKS_PER_SECOND = 20
private const val MAX_EFFECT_DURATION_SECONDS = Int.MAX_VALUE / TICKS_PER_SECOND

internal fun effectDurationTicks(seconds: Int): Int {
    require(seconds > 0 && seconds <= MAX_EFFECT_DURATION_SECONDS) {
        "core.effect.duration_seconds must be between 1 and $MAX_EFFECT_DURATION_SECONDS"
    }
    return Math.multiplyExact(seconds, TICKS_PER_SECOND)
}

object EffectConfig {
    @JvmField
    val EFFECT_DURATION_SECONDS = Option("core.effect.duration_seconds", 5,
        "the duration in seconds for which region effects are applied to players each tick cycle. Must be greater than lazy_ticker_seconds and no greater than $MAX_EFFECT_DURATION_SECONDS.") { obj: Config, path: String? ->
        val seconds = obj.getInt(path)
        effectDurationTicks(seconds)
        seconds
    }
}
