package com.imyvm.iwg.application.time

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.infra.TestPeriodProcessingStore
import java.time.Clock

object WorldGeoTestPeriodTracker {
    private val callbacks = mutableListOf<(NaturalPeriodTransition) -> Unit>()
    private var lastPeriodIds: Map<NaturalPeriodKind, String>? = null

    fun registerCallback(callback: (NaturalPeriodTransition) -> Unit) {
        callbacks.add(callback)
    }

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        WorldGeoTestPeriodService.currentPeriodIds(clock)

    fun activePeriodIdsForEvent(unixMillis: Long): Map<NaturalPeriodKind, String> =
        if (lastPeriodIds == null && TestPeriodProcessingStore.getProcessedPeriodIds().isEmpty()) emptyMap()
        else WorldGeoTestPeriodService.periodIds(unixMillis)

    fun status(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        lastPeriodIds ?: TestPeriodProcessingStore.getProcessedPeriodIds().takeIf { it.isNotEmpty() } ?: currentPeriodIds(clock)

    fun reset() {
        lastPeriodIds = null
        TestPeriodProcessingStore.reset()
    }

    fun emitMissedForDebug(kind: NaturalPeriodKind, previousId: String, currentId: String, unixMillis: Long = Clock.systemUTC().millis()): Int {
        val transitions = WorldGeoTestPeriodService.missedPeriodTransitions(kind, previousId, currentId, unixMillis)
        transitions.forEach(::emit)
        return transitions.size
    }

    fun process(clock: Clock = Clock.systemUTC()): Int {
        val current = currentPeriodIds(clock)
        val previous = lastPeriodIds ?: TestPeriodProcessingStore.getProcessedPeriodIds().takeIf { it.isNotEmpty() }
        lastPeriodIds = current
        if (previous == null) {
            TestPeriodProcessingStore.replaceProcessedPeriodIds(current)
            return 0
        }
        var count = 0
        val unixMillis = clock.millis()
        for ((kind, currentId) in current) {
            val previousId = previous[kind] ?: continue
            val transitions = WorldGeoTestPeriodService.missedPeriodTransitions(kind, previousId, currentId, unixMillis)
            transitions.forEach(::emit)
            count += transitions.size
        }
        TestPeriodProcessingStore.replaceProcessedPeriodIds(current)
        return count
    }

    internal fun resetForTest() {
        lastPeriodIds = null
        callbacks.clear()
    }

    private fun emit(transition: NaturalPeriodTransition) {
        callbacks.forEach { callback ->
            runCatching { callback(transition) }.onFailure {
                ImyvmWorldGeo.logger.warn("Test period transition subscriber threw: ${it.message}", it)
            }
        }
    }
}
