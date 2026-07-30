package com.imyvm.iwg.domain

enum class WorldGeoBehaviorCaptureState {
    ACTIVE,
    CAPTURE_SUSPENDED
}

data class WorldGeoMissingCaptureInterval(
    val startMillis: Long,
    val endMillis: Long?,
    val droppedEventCount: Long
)

enum class WorldGeoPeriodDataStatus {
    COMPLETE,
    INCOMPLETE,
    UNAVAILABLE
}

data class WorldGeoPeriodCompleteness(
    val key: NaturalPeriodKey,
    val status: WorldGeoPeriodDataStatus,
    val missingIntervals: List<WorldGeoMissingCaptureInterval>
)
