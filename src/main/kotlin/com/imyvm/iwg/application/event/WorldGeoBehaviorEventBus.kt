package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.config.CoreConfig

object WorldGeoBehaviorEventBus {
    private const val MAX_RECENT_EVENTS = 20
    private val recentEvents = ArrayDeque<WorldGeoBehaviorEvent>()
    private val dispatcher = AsyncCallbackDispatcher<WorldGeoBehaviorEvent>(
        "behavior-event-callback",
        { CoreConfig.ASYNC_CALLBACK_QUEUE_CAPACITY.value }
    )

    fun registerCallback(callback: (WorldGeoBehaviorEvent) -> Unit) {
        dispatcher.registerCallback(callback)
    }

    fun publish(event: WorldGeoBehaviorEvent) {
        runCatching { BehaviorStatsStore.record(event) }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to record behavior stats: ${it.message}", it) }
        recentEvents.addLast(event)
        while (recentEvents.size > MAX_RECENT_EVENTS) recentEvents.removeFirst()
        dispatcher.dispatch(event)
    }

    fun getRecentEvents(limit: Int = MAX_RECENT_EVENTS): List<WorldGeoBehaviorEvent> =
        recentEvents.takeLast(limit.coerceIn(0, MAX_RECENT_EVENTS))

    internal fun awaitCallbacksForTest(timeoutMillis: Long = 5_000L) {
        dispatcher.awaitIdleForTest(timeoutMillis)
    }

    internal fun clearForTest() {
        dispatcher.clearForTest()
        recentEvents.clear()
    }
}
