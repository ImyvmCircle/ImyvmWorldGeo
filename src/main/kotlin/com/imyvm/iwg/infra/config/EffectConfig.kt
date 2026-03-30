package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object EffectConfig {
    @JvmField
    val EFFECT_DURATION_SECONDS = Option("core.effect.duration_seconds", 5,
        "the duration in seconds for which region effects are applied to players each tick cycle. Must be greater than lazy_ticker_seconds to prevent effects from expiring between ticks.") { obj: Config, path: String? -> obj.getInt(path) }
}