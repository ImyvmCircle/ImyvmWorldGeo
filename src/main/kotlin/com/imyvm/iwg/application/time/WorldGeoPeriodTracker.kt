package com.imyvm.iwg.application.time

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import java.time.Clock

object WorldGeoPeriodTracker {
    private val callbacks = mutableListOf<(NaturalPeriodTransition) -> Unit>()
    private var lastPeriodIds: Map<NaturalPeriodKind, String>? = null

    fun registerCallback(callback: (NaturalPeriodTransition) -> Unit) {
        callbacks.add(callback)
    }

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        WorldGeoTimeService.currentNaturalPeriodIds(clock)

    fun process(clock: Clock = Clock.systemUTC()) {
        val current = currentPeriodIds(clock)
        val previous = lastPeriodIds
        lastPeriodIds = current
        if (previous == null) return
        val unixMillis = clock.millis()
        for ((kind, currentId) in current) {
            val previousId = previous[kind] ?: continue
            if (previousId != currentId) emit(NaturalPeriodTransition(kind, previousId, currentId, unixMillis))
        }
    }

    internal fun resetForTest() {
        lastPeriodIds = null
        callbacks.clear()
    }

    private fun emit(transition: NaturalPeriodTransition) {
        callbacks.forEach { callback ->
            runCatching { callback(transition) }.onFailure {
                ImyvmWorldGeo.logger.warn("Natural period transition subscriber threw: ${it.message}", it)
            }
        }
    }
}
