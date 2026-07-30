package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.registerPlayerStatsEvents
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.config.CoreConfig
import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.infra.RegionDatabase

private const val PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS = 60_000L

fun registerPlayerStats() {
    var lastPlayerStatsSavedAt = System.currentTimeMillis()
    var lastBehaviorStatsAttemptAt = lastPlayerStatsSavedAt
    var behaviorStatsRetrying = false
    registerPlayerStatsEvents()
    LazyTicker.registerTask { _ ->
        val now = System.currentTimeMillis()
        if (now - lastPlayerStatsSavedAt >= PLAYER_STATS_SNAPSHOT_INTERVAL_MILLIS) {
            RegionDatabase.savePlayerStatsSnapshot()
            lastPlayerStatsSavedAt = now
        }
        val behaviorStatsInterval = if (behaviorStatsRetrying) {
            CoreConfig.BEHAVIOR_STATS_FAILED_SAVE_RETRY_MILLIS.value.toLong()
        } else {
            CoreConfig.BEHAVIOR_STATS_SAVE_INTERVAL_MILLIS.value.toLong()
        }
        if (now - lastBehaviorStatsAttemptAt >= behaviorStatsInterval) {
            lastBehaviorStatsAttemptAt = now
            behaviorStatsRetrying = !BehaviorStatsStore.saveSnapshot(now)
        }
    }
}
