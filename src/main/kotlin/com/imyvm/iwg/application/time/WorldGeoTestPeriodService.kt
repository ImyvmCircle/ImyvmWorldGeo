package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import java.time.Clock
import java.time.Instant
import java.util.Locale

object WorldGeoTestPeriodService {
    const val PREFIX = "test5m"
    private const val TEST_MINUTE_MILLIS = 5_000L
    private const val TEST_HOUR_MILLIS = 30_000L
    private const val TEST_DAY_MILLIS = 60_000L
    private const val TEST_WEEK_MILLIS = 300_000L
    private const val TEST_MONTH_MILLIS = 1_200_000L

    fun currentPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> = periodIds(clock.millis())

    fun periodIds(unixMillis: Long): Map<NaturalPeriodKind, String> = linkedMapOf(
        NaturalPeriodKind.HOUR to format("hour", unixMillis, TEST_HOUR_MILLIS),
        NaturalPeriodKind.DAY to format("day", unixMillis, TEST_DAY_MILLIS),
        NaturalPeriodKind.WEEK to format("week", unixMillis, TEST_WEEK_MILLIS),
        NaturalPeriodKind.MONTH to format("month", unixMillis, TEST_MONTH_MILLIS)
    )

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

    private fun format(label: String, unixMillis: Long, lengthMillis: Long): String =
        String.format(Locale.ROOT, "%s:%s:%d", PREFIX, label, Math.floorDiv(unixMillis, lengthMillis))

    private fun parseIndex(periodId: String): Long? = periodId.substringAfterLast(':').toLongOrNull()

    private fun label(kind: NaturalPeriodKind): String = when (kind) {
        NaturalPeriodKind.HOUR -> "hour"
        NaturalPeriodKind.DAY -> "day"
        NaturalPeriodKind.WEEK -> "week"
        NaturalPeriodKind.MONTH -> "month"
    }

    internal fun testMinuteId(clock: Clock = Clock.systemUTC()): String = format("minute", clock.millis(), TEST_MINUTE_MILLIS)

    internal fun instantForTestIndex(index: Long): Instant = Instant.ofEpochMilli(index * TEST_WEEK_MILLIS)
}
