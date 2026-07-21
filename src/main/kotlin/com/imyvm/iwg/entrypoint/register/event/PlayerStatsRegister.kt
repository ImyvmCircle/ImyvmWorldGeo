package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.registerPlayerStatsEvents
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.infra.RegionDatabase

private const val PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS = 60_000L

fun registerPlayerStats() {
    var lastSavedAt = System.currentTimeMillis()
    registerPlayerStatsEvents()
    LazyTicker.registerTask { _ ->
        val now = System.currentTimeMillis()
        if (now - lastSavedAt < PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS) return@registerTask
        RegionDatabase.savePlayerStatsSnapshot()
        BehaviorStatsStore.saveSnapshot()
        lastSavedAt = now
    }
}
