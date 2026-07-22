package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.WorldGeoSubSpaceTransition
import com.imyvm.iwg.infra.config.CoreConfig

object SubSpaceTransitionEvent {
    private val dispatcher = AsyncCallbackDispatcher<WorldGeoSubSpaceTransition>(
        "subspace-transition-callback",
        { CoreConfig.ASYNC_CALLBACK_QUEUE_CAPACITY.value }
    )

    fun registerCallback(callback: (WorldGeoSubSpaceTransition) -> Unit) {
        dispatcher.registerCallback(callback)
    }

    internal fun publish(transition: WorldGeoSubSpaceTransition) {
        dispatcher.dispatch(transition)
    }

    internal fun awaitCallbacksForTest(timeoutMillis: Long = 5_000L) {
        dispatcher.awaitIdleForTest(timeoutMillis)
    }

    internal fun clearForTest() {
        dispatcher.clearForTest()
    }
}
