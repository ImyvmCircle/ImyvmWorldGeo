package com.imyvm.iwg.domain

import java.util.UUID

data class WorldGeoBehaviorStatsCheckpointRequest(
    val checkpointId: UUID,
    val query: WorldGeoBehaviorStatsPageQuery,
    val pageSize: Int
)

enum class WorldGeoBehaviorStatsCheckpointStatus {
    PUBLISHED,
    ALREADY_PUBLISHED,
    INCOMPLETE,
    VERSION_CONFLICT,
    UNAVAILABLE
}

data class WorldGeoBehaviorStatsCheckpointResult(
    val status: WorldGeoBehaviorStatsCheckpointStatus,
    val checkpointId: UUID,
    val cutoffSequence: Long?,
    val manifestVersion: Long?,
    val pageCount: Int?,
    val completeness: WorldGeoPeriodCompleteness
)

data class WorldGeoBehaviorStatsCheckpointPage(
    val checkpointId: UUID,
    val pageIndex: Int,
    val manifestVersion: Long,
    val entries: List<WorldGeoBehaviorStatsEntry>,
    val hasMore: Boolean
)
