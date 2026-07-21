package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.infra.BehaviorStatsStore

object WorldGeoBehaviorEventBus {
    private const val MAX_RECENT_EVENTS = 20
    private val callbacks = mutableListOf<(WorldGeoBehaviorEvent) -> Unit>()
    private val recentEvents = ArrayDeque<WorldGeoBehaviorEvent>()

    fun registerCallback(callback: (WorldGeoBehaviorEvent) -> Unit) {
        callbacks.add(callback)
    }

    fun publish(event: WorldGeoBehaviorEvent) {
        runCatching { BehaviorStatsStore.record(event) }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to record behavior stats: ${it.message}", it) }
        recentEvents.addLast(event)
        while (recentEvents.size > MAX_RECENT_EVENTS) recentEvents.removeFirst()
        for (callback in callbacks.toList()) {
            try {
                callback(event)
            } catch (error: Throwable) {
                ImyvmWorldGeo.logger.warn("WorldGeo behavior event subscriber threw: ${error.message}", error)
            }
        }
    }

    fun getRecentEvents(limit: Int = MAX_RECENT_EVENTS): List<WorldGeoBehaviorEvent> =
        recentEvents.takeLast(limit.coerceIn(0, MAX_RECENT_EVENTS))

    internal fun clearForTest() {
        callbacks.clear()
        recentEvents.clear()
    }
}
