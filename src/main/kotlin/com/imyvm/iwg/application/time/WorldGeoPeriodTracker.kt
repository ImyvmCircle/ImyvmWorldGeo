package com.imyvm.iwg.application.time

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.event.AsyncCallbackDispatcher
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.CompleteNaturalPeriodTransition
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.infra.config.CoreConfig
import com.imyvm.iwg.infra.PeriodProcessingStore
import com.imyvm.iwg.infra.TestPeriodModeStore
import com.imyvm.iwg.infra.PeriodTimelineStore
import java.time.Clock

object WorldGeoPeriodTracker {
    private val dispatcher = AsyncCallbackDispatcher<NaturalPeriodTransition>(
        "natural-period-callback",
        { CoreConfig.ASYNC_CALLBACK_QUEUE_CAPACITY.value }
    )
    private val completeDispatcher = AsyncCallbackDispatcher<CompleteNaturalPeriodTransition>(
        "complete-natural-period-callback",
        { CoreConfig.ASYNC_CALLBACK_QUEUE_CAPACITY.value }
    )
    private var lastProductionPeriodIds: Map<NaturalPeriodKind, String>? = null
    private var lastTestPeriodIds: Map<NaturalPeriodKind, String>? = null

    fun registerCallback(callback: (NaturalPeriodTransition) -> Unit) {
        dispatcher.registerCallback(callback)
    }

    fun registerCompleteCallback(callback: (CompleteNaturalPeriodTransition) -> Unit) {
        completeDispatcher.registerCallback(callback)
    }

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        WorldGeoTimeService.currentNaturalPeriodIds(clock)

    fun currentPeriodKeys(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, NaturalPeriodKey> =
        WorldGeoPeriodTimelineService.currentPeriodKeys(clock)

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
            val endedAtMillis = TestPeriodModeStore.currentState()?.endAtMillis ?: clock.millis()
            resumeNaturalWithoutBackfill(clock)
            TestPeriodModeStore.clear()
            PeriodTimelineStore.closeActiveTestTimeline(endedAtMillis)
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
        dispatcher.clearForTest()
        completeDispatcher.clearForTest()
    }

    internal fun awaitCallbacksForTest(timeoutMillis: Long = 5_000L) {
        dispatcher.awaitIdleForTest(timeoutMillis)
        completeDispatcher.awaitIdleForTest(timeoutMillis)
    }

    private fun emit(transition: NaturalPeriodTransition) {
        val timelineId = PeriodTimelineStore.activeTimeline().timelineId
        completeDispatcher.dispatch(CompleteNaturalPeriodTransition(
            NaturalPeriodKey(timelineId, transition.kind, transition.previousId),
            NaturalPeriodKey(timelineId, transition.kind, transition.currentId),
            transition.unixMillis
        ))
        dispatcher.dispatch(transition)
    }
}
