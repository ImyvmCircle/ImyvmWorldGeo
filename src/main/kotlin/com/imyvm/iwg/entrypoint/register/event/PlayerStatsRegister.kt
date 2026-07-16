package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.registerPlayerStatsEvents
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.infra.RegionDatabase

private const val PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS = 60_000L

fun registerPlayerStats() {
    var lastSavedAt = System.currentTimeMillis()
    registerPlayerStatsEvents()
    LazyTicker.registerTask { _ ->
        lastSavedAt = updatePlayerStatsSnapshotTime(
            lastSavedAt,
            System.currentTimeMillis(),
            PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS
        ) {
            RegionDatabase.trySavePlayerStatsSnapshot()
        }
    }
}

internal fun updatePlayerStatsSnapshotTime(
    lastSuccessfulAt: Long,
    now: Long,
    intervalMillis: Long,
    save: () -> Boolean
): Long {
    require(lastSuccessfulAt >= 0L) { "last successful snapshot time must be non-negative" }
    require(now >= 0L) { "current snapshot time must be non-negative" }
    require(intervalMillis > 0L) { "snapshot interval must be positive" }
    if (now < lastSuccessfulAt || now - lastSuccessfulAt < intervalMillis) return lastSuccessfulAt
    return if (save()) now else lastSuccessfulAt
}
