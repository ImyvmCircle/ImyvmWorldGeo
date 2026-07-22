package com.imyvm.iwg.application.time

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.infra.PeriodProcessingStore
import java.time.Clock

object WorldGeoPeriodTracker {
    private val callbacks = mutableListOf<(NaturalPeriodTransition) -> Unit>()
    private var lastPeriodIds: Map<NaturalPeriodKind, String>? = null

    fun registerCallback(callback: (NaturalPeriodTransition) -> Unit) {
        callbacks.add(callback)
    }

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        WorldGeoTimeService.currentNaturalPeriodIds(clock)

    fun emitMissedForDebug(kind: NaturalPeriodKind, previousId: String, currentId: String, unixMillis: Long = Clock.systemUTC().millis()): Int {
        val transitions = WorldGeoTimeService.missedPeriodTransitions(kind, previousId, currentId, unixMillis)
        transitions.forEach(::emit)
        return transitions.size
    }

    fun process(clock: Clock = Clock.systemUTC()) {
        val current = currentPeriodIds(clock)
        val previous = lastPeriodIds ?: PeriodProcessingStore.getProcessedPeriodIds().takeIf { it.isNotEmpty() }
        lastPeriodIds = current
        if (previous == null) {
            PeriodProcessingStore.replaceProcessedPeriodIds(current)
            return
        }
        val unixMillis = clock.millis()
        for ((kind, currentId) in current) {
            val previousId = previous[kind] ?: continue
            WorldGeoTimeService.missedPeriodTransitions(kind, previousId, currentId, unixMillis).forEach(::emit)
        }
        PeriodProcessingStore.replaceProcessedPeriodIds(current)
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
