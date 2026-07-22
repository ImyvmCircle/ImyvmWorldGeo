package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object TestPeriodConfig {
    @JvmField
    val TEST_WEEK_LENGTH_SECONDS = Option(
        "core.test_period.week_length_seconds",
        840,
        "the length of one temporary test week in seconds while WorldGeo test period mode is active."
    ) { obj: Config, path: String? -> positiveInt(path, obj.getInt(path)) }
}
