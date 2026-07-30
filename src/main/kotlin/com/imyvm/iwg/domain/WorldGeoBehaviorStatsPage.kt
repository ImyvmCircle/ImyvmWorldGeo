package com.imyvm.iwg.domain

import java.util.UUID

data class WorldGeoBehaviorStatsPageQuery(
    val periodKey: NaturalPeriodKey,
    val regionId: Int,
    val scopeId: Long? = null,
    val subSpaceId: Long? = null,
    val behaviorType: WorldGeoBehaviorType? = null,
    val objectIds: Set<String>? = null,
    val playerUuids: Set<UUID>? = null
)

enum class WorldGeoBehaviorStatsStreamOpenStatus {
    OPENED,
    BUSY,
    CLOSED,
    UNAVAILABLE
}

data class WorldGeoBehaviorStatsStreamOpenResult(
    val status: WorldGeoBehaviorStatsStreamOpenStatus,
    val handleId: UUID?,
    val manifestVersion: Long?
)

enum class WorldGeoBehaviorStatsPageReadStatus {
    PAGE,
    BUSY,
    CLOSED
}

data class WorldGeoBehaviorStatsPage(
    val manifestVersion: Long,
    val entries: List<WorldGeoBehaviorStatsEntry>,
    val hasMore: Boolean,
    val completeness: WorldGeoPeriodCompleteness
)

data class WorldGeoBehaviorStatsPageReadResult(
    val status: WorldGeoBehaviorStatsPageReadStatus,
    val page: WorldGeoBehaviorStatsPage?
)

data class WorldGeoBlockDeltaBreakdown(
    val placedCount: Long,
    val brokenCount: Long,
    val netDelta: Long,
    val playerContributions: Map<UUID, Long>
)

data class WorldGeoBatchBlockDeltaStats(
    val periodKey: NaturalPeriodKey,
    val regionId: Int,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val blocks: Map<String, WorldGeoBlockDeltaBreakdown>,
    val completeness: WorldGeoPeriodCompleteness,
    val manifestVersion: Long
)
