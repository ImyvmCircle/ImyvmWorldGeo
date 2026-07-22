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
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_MAX_ENTRY_COUNT = Option(
        "core.behavior_stats.max_entry_count",
        1_000_000,
        "the maximum number of behavior stat entries retained before old periods are evicted."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val ASYNC_CALLBACK_QUEUE_CAPACITY = Option(
        "core.async_callback_queue_capacity",
        1024,
        "the maximum number of immutable callback payloads queued for asynchronous delivery."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }
}
