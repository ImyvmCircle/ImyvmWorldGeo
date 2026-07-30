package com.imyvm.iwg.domain

enum class NaturalPeriodTimelineType {
    PRODUCTION,
    TEST
}

data class NaturalPeriodKey(
    val timelineId: String,
    val kind: NaturalPeriodKind,
    val periodId: String
)

data class NaturalPeriodBounds(
    val key: NaturalPeriodKey,
    val startMillis: Long,
    val endMillis: Long
)

data class NaturalPeriodTimeline(
    val timelineId: String,
    val type: NaturalPeriodTimelineType,
    val sequence: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val closed: Boolean,
    val testWeekCount: Int?,
    val testWeekLengthMillis: Long?
)

data class NaturalPeriodRange(
    val timelineId: String,
    val kind: NaturalPeriodKind,
    val earliest: NaturalPeriodKey,
    val latest: NaturalPeriodKey
)

data class CompleteNaturalPeriodTransition(
    val previous: NaturalPeriodKey,
    val current: NaturalPeriodKey,
    val unixMillis: Long
)
