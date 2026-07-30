package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.NaturalPeriodBounds
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodRange
import com.imyvm.iwg.domain.NaturalPeriodTimeline
import com.imyvm.iwg.domain.NaturalPeriodTimelineType
import com.imyvm.iwg.infra.PeriodTimelineStore
import com.imyvm.iwg.infra.TestPeriodModeState
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object WorldGeoPeriodTimelineService {
    fun currentPeriodKeys(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, NaturalPeriodKey> {
        val timeline = if (TestPeriodModeService.activeState(clock) != null) {
            PeriodTimelineStore.activeTimeline()
        } else {
            requireNotNull(PeriodTimelineStore.getTimeline(PeriodTimelineStore.PRODUCTION_TIMELINE_ID))
        }
        return periodIds(timeline, clock.millis()).mapValues { (kind, periodId) ->
            NaturalPeriodKey(timeline.timelineId, kind, periodId)
        }
    }

    fun availableTimelines(): List<NaturalPeriodTimeline> = PeriodTimelineStore.getTimelines()

    fun availablePeriodRange(
        timelineId: String,
        kind: NaturalPeriodKind,
        clock: Clock = Clock.systemUTC()
    ): NaturalPeriodRange? {
        val timeline = PeriodTimelineStore.getTimeline(timelineId) ?: return null
        val observedEnd = timeline.endedAtMillis ?: clock.millis()
        val configuredEnd = if (timeline.type == NaturalPeriodTimelineType.TEST) {
            runCatching {
                Math.addExact(
                    timeline.startedAtMillis,
                    Math.multiplyExact(
                        requireNotNull(timeline.testWeekCount).toLong(),
                        requireNotNull(timeline.testWeekLengthMillis)
                    )
                )
            }.getOrNull() ?: return null
        } else Long.MAX_VALUE
        val endExclusive = minOf(observedEnd, configuredEnd)
        val latestSample = maxOf(timeline.startedAtMillis, endExclusive - 1L)
        val earliestId = periodIds(timeline, timeline.startedAtMillis).getValue(kind)
        val latestId = periodIds(timeline, latestSample).getValue(kind)
        return NaturalPeriodRange(
            timelineId,
            kind,
            NaturalPeriodKey(timelineId, kind, earliestId),
            NaturalPeriodKey(timelineId, kind, latestId)
        )
    }

    fun periodBounds(key: NaturalPeriodKey): NaturalPeriodBounds? {
        val timeline = PeriodTimelineStore.getTimeline(key.timelineId) ?: return null
        return when (timeline.type) {
            NaturalPeriodTimelineType.PRODUCTION -> productionBounds(key)
            NaturalPeriodTimelineType.TEST -> testBounds(timeline, key)
        }
    }

    private fun periodIds(timeline: NaturalPeriodTimeline, unixMillis: Long): Map<NaturalPeriodKind, String> =
        when (timeline.type) {
            NaturalPeriodTimelineType.PRODUCTION -> WorldGeoTimeService.naturalPeriodIds(
                Clock.fixed(java.time.Instant.ofEpochMilli(unixMillis), java.time.ZoneOffset.UTC)
            )
            NaturalPeriodTimelineType.TEST -> TestPeriodModeService.periodIds(testState(timeline), unixMillis)
        }

    private fun productionBounds(key: NaturalPeriodKey): NaturalPeriodBounds? = runCatching {
        val start = when (key.kind) {
            NaturalPeriodKind.HOUR -> LocalDateTime.parse(key.periodId, HOUR_FORMATTER)
            NaturalPeriodKind.DAY -> LocalDate.parse(key.periodId).atStartOfDay()
            NaturalPeriodKind.WEEK -> LocalDate.parse("${key.periodId}-1", DateTimeFormatter.ISO_WEEK_DATE).atStartOfDay()
            NaturalPeriodKind.MONTH -> YearMonth.parse(key.periodId).atDay(1).atStartOfDay()
            NaturalPeriodKind.YEAR -> Year.parse(key.periodId).atDay(1).atStartOfDay()
        }
        val end = when (key.kind) {
            NaturalPeriodKind.HOUR -> start.plusHours(1)
            NaturalPeriodKind.DAY -> start.plusDays(1)
            NaturalPeriodKind.WEEK -> start.plusWeeks(1)
            NaturalPeriodKind.MONTH -> start.plusMonths(1)
            NaturalPeriodKind.YEAR -> start.plusYears(1)
        }
        NaturalPeriodBounds(
            key,
            start.atZone(WorldGeoTimeService.DEFAULT_ZONE).toInstant().toEpochMilli(),
            end.atZone(WorldGeoTimeService.DEFAULT_ZONE).toInstant().toEpochMilli()
        )
    }.getOrNull()

    private fun testBounds(timeline: NaturalPeriodTimeline, key: NaturalPeriodKey): NaturalPeriodBounds? {
        val prefix = "test:${label(key.kind)}:"
        if (!key.periodId.startsWith(prefix)) return null
        val index = key.periodId.removePrefix(prefix).toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val length = testPeriodLength(timeline, key.kind)
        val start = runCatching {
            Math.addExact(timeline.startedAtMillis, Math.multiplyExact(index, length))
        }.getOrNull() ?: return null
        val configuredEnd = runCatching {
            Math.addExact(
                timeline.startedAtMillis,
                Math.multiplyExact(requireNotNull(timeline.testWeekCount).toLong(), requireNotNull(timeline.testWeekLengthMillis))
            )
        }.getOrNull() ?: return null
        if (start >= minOf(timeline.endedAtMillis ?: configuredEnd, configuredEnd)) return null
        return NaturalPeriodBounds(key, start, runCatching { Math.addExact(start, length) }.getOrNull() ?: return null)
    }

    private fun testState(timeline: NaturalPeriodTimeline) = TestPeriodModeState(
        timeline.startedAtMillis,
        requireNotNull(timeline.testWeekCount),
        requireNotNull(timeline.testWeekLengthMillis)
    )

    private fun testPeriodLength(timeline: NaturalPeriodTimeline, kind: NaturalPeriodKind): Long {
        val week = requireNotNull(timeline.testWeekLengthMillis)
        val day = (week / 7L).coerceAtLeast(1L)
        val hour = (day / 24L).coerceAtLeast(1L)
        return when (kind) {
            NaturalPeriodKind.HOUR -> hour
            NaturalPeriodKind.DAY -> day
            NaturalPeriodKind.WEEK -> week
            NaturalPeriodKind.MONTH -> Math.multiplyExact(week, 4L)
            NaturalPeriodKind.YEAR -> Math.multiplyExact(week, 48L)
        }
    }

    private fun label(kind: NaturalPeriodKind): String = kind.name.lowercase()

    private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
}
