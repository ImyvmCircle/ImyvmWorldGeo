package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object CoreConfig {
    @JvmField
    val LANGUAGE = Option(
        "core.language",
        "zh_cn",
        "the display language of Imyvm World Geo."
    ) { obj: Config, path: String? ->
        obj.getString(path)
    }

    @JvmField
    val LAZY_TICKER_SECONDS = Option(
        "core.lazy_ticker_seconds",
        1,
        "the interval in seconds for lazy ticker tasks to run."
    ) { obj: Config, path: String? ->
        obj.getInt(path)
    }
}