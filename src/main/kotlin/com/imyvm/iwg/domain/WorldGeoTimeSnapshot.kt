package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier
import java.time.LocalDateTime

data class WorldGeoTimeSnapshot(
    val game: GameTimeSnapshot,
    val real: RealTimeSnapshot
)

data class GameTimeSnapshot(
    val dimensionId: Identifier,
    val gameTick: Long,
    val dayTimeTick: Long,
    val gameDay: Long,
    val dayTick: Long,
    val raining: Boolean,
    val thundering: Boolean,
    val moonPhase: Int
)

data class RealTimeSnapshot(
    val unixMillis: Long,
    val unixSeconds: Long,
    val zoneId: String,
    val localDateTime: LocalDateTime,
    val naturalHour: String,
    val naturalDay: String,
    val naturalWeek: String,
    val naturalMonth: String,
    val naturalYear: Int
)
