package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.GameTimeSnapshot
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.NaturalPeriodTransition
import com.imyvm.iwg.domain.RealTimeSnapshot
import com.imyvm.iwg.domain.WorldGeoTimeSnapshot
import net.minecraft.server.level.ServerLevel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object WorldGeoTimeService {
    val DEFAULT_ZONE: ZoneId = ZoneOffset.ofHours(8)

    fun snapshot(level: ServerLevel, clock: Clock = Clock.systemUTC()): WorldGeoTimeSnapshot =
        WorldGeoTimeSnapshot(
            game = gameSnapshot(level),
            real = realSnapshot(clock.instant(), DEFAULT_ZONE)
        )

    internal fun realSnapshot(instant: Instant, zoneId: ZoneId): RealTimeSnapshot {
        val zoned = instant.atZone(zoneId)
        val local = zoned.toLocalDateTime()
        val weekFields = WeekFields.ISO
        val weekYear = local.get(weekFields.weekBasedYear())
        val week = local.get(weekFields.weekOfWeekBasedYear())
        return RealTimeSnapshot(
            unixMillis = instant.toEpochMilli(),
            unixSeconds = instant.epochSecond,
            zoneId = zoneId.id,
            localDateTime = local,
            naturalHour = local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")),
            naturalDay = local.toLocalDate().toString(),
            naturalWeek = String.format(Locale.ROOT, "%04d-W%02d", weekYear, week),
            naturalMonth = local.format(DateTimeFormatter.ofPattern("yyyy-MM")),
            naturalYear = local.year
        )
    }

    private fun gameSnapshot(level: ServerLevel): GameTimeSnapshot {
        val dayTime = level.gameTime
        return GameTimeSnapshot(
            dimensionId = level.dimension().identifier(),
            gameTick = level.gameTime,
            dayTimeTick = dayTime,
            gameDay = Math.floorDiv(dayTime, TICKS_PER_DAY),
            dayTick = Math.floorMod(dayTime, TICKS_PER_DAY),
            raining = level.isRaining,
            thundering = level.isThundering,
            moonPhase = moonPhase(dayTime)
        )
    }

    fun currentNaturalPeriodIds(clock: Clock = Clock.systemUTC()): Map<NaturalPeriodKind, String> =
        naturalPeriodIds(realSnapshot(clock.instant(), DEFAULT_ZONE))

    internal fun missedPeriodTransitions(
        kind: NaturalPeriodKind,
        previousId: String,
        currentId: String,
        unixMillis: Long
    ): List<NaturalPeriodTransition> {
        if (previousId == currentId) return emptyList()
        return runCatching { enumeratePeriodIds(kind, previousId, currentId) }
            .getOrElse { listOf(currentId) }
            .map { nextId -> NaturalPeriodTransition(kind, previousIdFor(kind, previousId, nextId), nextId, unixMillis) }
    }

    private fun previousIdFor(kind: NaturalPeriodKind, firstPreviousId: String, currentId: String): String = when (kind) {
        NaturalPeriodKind.HOUR -> LocalDateTime.parse(currentId, HOUR_FORMATTER).minusHours(1).format(HOUR_FORMATTER)
        NaturalPeriodKind.DAY -> LocalDate.parse(currentId).minusDays(1).toString()
        NaturalPeriodKind.WEEK -> formatWeek(parseWeekStart(currentId).minusWeeks(1))
        NaturalPeriodKind.MONTH -> YearMonth.parse(currentId).minusMonths(1).toString()
    }.let { if (it < firstPreviousId) firstPreviousId else it }

    private fun enumeratePeriodIds(kind: NaturalPeriodKind, previousId: String, currentId: String): List<String> = when (kind) {
        NaturalPeriodKind.HOUR -> enumerate(
            LocalDateTime.parse(previousId, HOUR_FORMATTER),
            LocalDateTime.parse(currentId, HOUR_FORMATTER),
            { it.plusHours(1) },
            { it.format(HOUR_FORMATTER) }
        )
        NaturalPeriodKind.DAY -> enumerate(
            LocalDate.parse(previousId),
            LocalDate.parse(currentId),
            { it.plusDays(1) },
            { it.toString() }
        )
        NaturalPeriodKind.WEEK -> enumerate(
            parseWeekStart(previousId),
            parseWeekStart(currentId),
            { it.plusWeeks(1) },
            ::formatWeek
        )
        NaturalPeriodKind.MONTH -> enumerate(
            YearMonth.parse(previousId),
            YearMonth.parse(currentId),
            { it.plusMonths(1) },
            { it.toString() }
        )
    }

    private fun <T : Comparable<T>> enumerate(previous: T, current: T, next: (T) -> T, format: (T) -> String): List<String> {
        require(previous < current) { "previous period must be before current period" }
        val result = mutableListOf<String>()
        var value = next(previous)
        while (value <= current) {
            result.add(format(value))
            value = next(value)
        }
        return result
    }

    private fun parseWeekStart(periodId: String): LocalDate =
        LocalDate.parse("$periodId-1", DateTimeFormatter.ISO_WEEK_DATE)

    private fun formatWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val weekYear = date.get(weekFields.weekBasedYear())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        return String.format(Locale.ROOT, "%04d-W%02d", weekYear, week)
    }

    internal fun naturalPeriodIds(snapshot: RealTimeSnapshot): Map<NaturalPeriodKind, String> = linkedMapOf(
        NaturalPeriodKind.HOUR to snapshot.naturalHour,
        NaturalPeriodKind.DAY to snapshot.naturalDay,
        NaturalPeriodKind.WEEK to snapshot.naturalWeek,
        NaturalPeriodKind.MONTH to snapshot.naturalMonth
    )

    internal fun moonPhase(dayTime: Long): Int = Math.floorMod(Math.floorDiv(dayTime, TICKS_PER_DAY), 8L).toInt()

    private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
    private const val TICKS_PER_DAY = 24_000L
}
