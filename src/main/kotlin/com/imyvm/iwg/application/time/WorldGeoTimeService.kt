package com.imyvm.iwg.application.time

import com.imyvm.iwg.domain.GameTimeSnapshot
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.RealTimeSnapshot
import com.imyvm.iwg.domain.WorldGeoTimeSnapshot
import net.minecraft.server.level.ServerLevel
import java.time.Clock
import java.time.Instant
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

    internal fun naturalPeriodIds(snapshot: RealTimeSnapshot): Map<NaturalPeriodKind, String> = linkedMapOf(
        NaturalPeriodKind.HOUR to snapshot.naturalHour,
        NaturalPeriodKind.DAY to snapshot.naturalDay,
        NaturalPeriodKind.WEEK to snapshot.naturalWeek,
        NaturalPeriodKind.MONTH to snapshot.naturalMonth
    )

    internal fun moonPhase(dayTime: Long): Int = Math.floorMod(Math.floorDiv(dayTime, TICKS_PER_DAY), 8L).toInt()

    private const val TICKS_PER_DAY = 24_000L
}
