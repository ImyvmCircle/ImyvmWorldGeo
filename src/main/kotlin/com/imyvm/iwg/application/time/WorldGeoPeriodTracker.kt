package com.imyvm.iwg.application.time

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.infra.PeriodProcessingStore
import com.imyvm.iwg.infra.TestPeriodModeStore
import java.time.Clock

object WorldGeoPeriodTracker {
    private val callbacks = mutableListOf<(NaturalPeriodTransition) -> Unit>()
    private var lastProductionPeriodIds: Map<NaturalPeriodKind, String>? = null
    private var lastTestPeriodIds: Map<NaturalPeriodKind, String>? = null

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

    fun resumeNaturalWithoutBackfill(clock: Clock = Clock.systemUTC()) {
        val current = WorldGeoTimeService.naturalPeriodIds(clock)
        lastTestPeriodIds = null
        lastProductionPeriodIds = current
        PeriodProcessingStore.replaceProcessedPeriodIds(current)
    }

    fun process(clock: Clock = Clock.systemUTC()) {
        val hadTestMode = TestPeriodModeStore.currentState() != null
        val testState = TestPeriodModeService.activeState(clock)
        if (testState != null) {
            val current = TestPeriodModeService.currentPeriodIds(clock) ?: return
            val previous = lastTestPeriodIds ?: TestPeriodModeStore.getProcessedPeriodIds().takeIf { it.isNotEmpty() }
            lastTestPeriodIds = current
            lastProductionPeriodIds = WorldGeoTimeService.naturalPeriodIds(clock)
            if (previous == null) {
                TestPeriodModeStore.replaceProcessedPeriodIds(current)
                return
            }
            val unixMillis = clock.millis()
            for ((kind, currentId) in current) {
                val previousId = previous[kind] ?: continue
                WorldGeoTimeService.missedPeriodTransitions(kind, previousId, currentId, unixMillis).forEach(::emit)
            }
            TestPeriodModeStore.replaceProcessedPeriodIds(current)
            return
        }
        val current = WorldGeoTimeService.naturalPeriodIds(clock)
        if (hadTestMode) {
            resumeNaturalWithoutBackfill(clock)
            TestPeriodModeStore.clear()
            return
        }
        val previous = lastProductionPeriodIds ?: PeriodProcessingStore.getProcessedPeriodIds().takeIf { it.isNotEmpty() }
        lastProductionPeriodIds = current
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
        lastProductionPeriodIds = null
        lastTestPeriodIds = null
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
