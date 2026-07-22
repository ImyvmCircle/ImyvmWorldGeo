package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.infra.TestPeriodModeState
import com.imyvm.iwg.infra.TestPeriodModeStore
import com.imyvm.iwg.infra.config.TestPeriodConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TestPeriodModeStatus(
    val active: Boolean,
    val weekLengthSeconds: Int,
    val startedAtMillis: Long?,
    val remainingMillis: Long,
    val weekCount: Int,
    val currentWeek: Int,
    val periodIds: Map<NaturalPeriodKind, String>
)

object TestPeriodModeService {
    const val DEFAULT_WEEK_COUNT = 3
    const val PREFIX = "test"

    fun start(weekCount: Int = DEFAULT_WEEK_COUNT, clock: Clock = Clock.systemUTC()): TestPeriodModeStatus {
        require(weekCount > 0) { "test period week count must be positive" }
        val state = TestPeriodModeState(
            startedAtMillis = clock.millis(),
            weekCount = weekCount,
            weekLengthMillis = TestPeriodConfig.TEST_WEEK_LENGTH_SECONDS.value * 1000L
        )
        val ids = periodIds(state, clock.millis())
        TestPeriodModeStore.replaceState(state, ids)
        return status(clock)
    }

    fun stop() {
        TestPeriodModeStore.clear()
    }

    fun activeState(clock: Clock = Clock.systemUTC()): TestPeriodModeState? {
        val state = TestPeriodModeStore.currentState() ?: return null
        return state.takeIf { clock.millis() < it.endAtMillis }
    }

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String>? =
        activeState(clock)?.let { periodIds(it, clock.millis()) }

    fun status(clock: Clock = Clock.systemUTC()): TestPeriodModeStatus {
        val active = activeState(clock)
        val now = clock.millis()
        val weekLengthSeconds = TestPeriodConfig.TEST_WEEK_LENGTH_SECONDS.value
        if (active == null) {
            return TestPeriodModeStatus(false, weekLengthSeconds, null, 0L, 0, 0, emptyMap())
        }
        val elapsed = (now - active.startedAtMillis).coerceAtLeast(0L)
        val currentWeek = (elapsed / active.weekLengthMillis).toInt().coerceIn(0, active.weekCount - 1) + 1
        return TestPeriodModeStatus(
            active = true,
            weekLengthSeconds = weekLengthSeconds,
            startedAtMillis = active.startedAtMillis,
            remainingMillis = (active.endAtMillis - now).coerceAtLeast(0L),
            weekCount = active.weekCount,
            currentWeek = currentWeek,
            periodIds = periodIds(active, now)
        )
    }

    fun missedPeriodTransitions(
        kind: NaturalPeriodKind,
        previousId: String,
        currentId: String,
        unixMillis: Long
    ): List<NaturalPeriodTransition> {
        if (previousId == currentId) return emptyList()
        val previousIndex = parseIndex(previousId) ?: return listOf(NaturalPeriodTransition(kind, previousId, currentId, unixMillis))
        val currentIndex = parseIndex(currentId) ?: return listOf(NaturalPeriodTransition(kind, previousId, currentId, unixMillis))
        if (previousIndex >= currentIndex) return emptyList()
        val label = label(kind)
        return ((previousIndex + 1)..currentIndex).map { index ->
            NaturalPeriodTransition(kind, "$PREFIX:$label:${index - 1}", "$PREFIX:$label:$index", unixMillis)
        }
    }

    fun isTestPeriodId(periodId: String): Boolean = periodId.startsWith("$PREFIX:")

    fun formatStartedAt(startedAtMillis: Long, zoneId: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(startedAtMillis).atZone(zoneId))

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    internal fun periodIds(state: TestPeriodModeState, unixMillis: Long): Map<NaturalPeriodKind, String> {
        val elapsed = (unixMillis - state.startedAtMillis).coerceAtLeast(0L)
        val weekLength = state.weekLengthMillis
        val dayLength = (weekLength / 7L).coerceAtLeast(1L)
        val hourLength = (dayLength / 24L).coerceAtLeast(1L)
        val monthLength = Math.multiplyExact(weekLength, 4L)
        return linkedMapOf(
            NaturalPeriodKind.HOUR to format("hour", elapsed, hourLength),
            NaturalPeriodKind.DAY to format("day", elapsed, dayLength),
            NaturalPeriodKind.WEEK to format("week", elapsed, weekLength),
            NaturalPeriodKind.MONTH to format("month", elapsed, monthLength)
        )
    }

    private fun format(label: String, elapsedMillis: Long, lengthMillis: Long): String =
        String.format(Locale.ROOT, "%s:%s:%d", PREFIX, label, Math.floorDiv(elapsedMillis, lengthMillis))

    private fun parseIndex(periodId: String): Long? = periodId.substringAfterLast(':').toLongOrNull()

    private fun label(kind: NaturalPeriodKind): String = when (kind) {
        NaturalPeriodKind.HOUR -> "hour"
        NaturalPeriodKind.DAY -> "day"
        NaturalPeriodKind.WEEK -> "week"
        NaturalPeriodKind.MONTH -> "month"
    }
}
