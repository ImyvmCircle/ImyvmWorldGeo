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
        "the hard limit for pending behavior stat entries before capture is suspended."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_WARNING_ENTRY_COUNT = Option(
        "core.behavior_stats.warning_entry_count",
        750_000,
        "the pending behavior stat entry count that triggers a storage warning."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_MAX_ESTIMATED_BYTES = Option(
        "core.behavior_stats.max_estimated_bytes",
        134_217_728,
        "the hard estimated byte limit for pending behavior stats before capture is suspended."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES = Option(
        "core.behavior_stats.warning_estimated_bytes",
        100_663_296,
        "the pending behavior stat estimated bytes that trigger a storage warning."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_SAVE_INTERVAL_MILLIS = Option(
        "core.behavior_stats.save_interval_millis",
        3_600_000,
        "the normal interval in milliseconds for behavior stats snapshots."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_FAILED_SAVE_RETRY_MILLIS = Option(
        "core.behavior_stats.failed_save_retry_millis",
        60_000,
        "the retry interval in milliseconds after a behavior stats snapshot fails."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_SHORT_PERIOD_RETENTION_MONTHS = Option(
        "core.behavior_stats.short_period_retention_months",
        2,
        "the calendar-month retention for hour, day and week behavior stats after period end."
    ) { obj: Config, path: String? ->
        positiveInt(path, obj.getInt(path))
    }

    @JvmField
    val BEHAVIOR_STATS_MONTH_RETENTION_YEARS = Option(
        "core.behavior_stats.month_retention_years",
        1,
        "the calendar-year retention for month behavior stats after period end."
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
