package com.imyvm.iwg.domain

import java.util.UUID

data class WorldGeoBehaviorStatsQuery(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val behaviorType: WorldGeoBehaviorType? = null,
    val regionId: Int? = null,
    val scopeId: Long? = null,
    val subSpaceId: Long? = null,
    val playerUuid: UUID? = null,
    val objectId: String? = null,
    val targetId: String? = null
) {
    constructor(
        periodKind: NaturalPeriodKind,
        periodId: String,
        behaviorType: WorldGeoBehaviorType?,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID?,
        objectId: String?
    ) : this(periodKind, periodId, behaviorType, regionId, scopeId, subSpaceId, playerUuid, objectId, null)
}

data class WorldGeoBehaviorStatsEntry(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val behaviorType: WorldGeoBehaviorType,
    val regionId: Int,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val playerUuid: UUID,
    val objectId: String?,
    val targetId: String?,
    val count: Long
) {
    constructor(
        periodKind: NaturalPeriodKind,
        periodId: String,
        behaviorType: WorldGeoBehaviorType,
        regionId: Int,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID,
        objectId: String?,
        count: Long
    ) : this(periodKind, periodId, behaviorType, regionId, scopeId, subSpaceId, playerUuid, objectId, null, count)
}

data class WorldGeoBlockDeltaStats(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val regionId: Int?,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val blockFilter: String?,
    val placedCount: Long,
    val brokenCount: Long,
    val netDelta: Long,
    val playerContributions: Map<UUID, Long>
)

data class WorldGeoResidenceStats(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val regionId: Int?,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val chunkResidenceMillis: Map<String, Long>,
    val averageResidenceMillis: Long,
    val totalResidenceMillis: Long
)

data class WorldGeoCombatPlayerStats(
    val damageCount: Long,
    val killCount: Long,
    val deathCount: Long,
    val damagedCount: Long
)

data class WorldGeoCombatTargetStats(
    val damageCount: Long,
    val killCount: Long
)

data class WorldGeoEntityCombatStats(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val regionId: Int?,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val objectFilter: String?,
    val targetFilter: String?,
    val damageCount: Long,
    val killCount: Long,
    val deathCount: Long,
    val damagedCount: Long,
    val playerStats: Map<UUID, WorldGeoCombatPlayerStats>,
    val targetStats: Map<String, WorldGeoCombatTargetStats>
) {
    constructor(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        objectFilter: String?,
        damageCount: Long,
        killCount: Long,
        deathCount: Long,
        damagedCount: Long,
        playerStats: Map<UUID, WorldGeoCombatPlayerStats>
    ) : this(
        periodKind, periodId, regionId, scopeId, subSpaceId, objectFilter, null,
        damageCount, killCount, deathCount, damagedCount, playerStats, emptyMap()
    )
}

data class WorldGeoPlayerOnlineTimeStats(
    val onlineMillis: Long,
    val afkMillis: Long,
    val nonAfkMillis: Long
)

data class WorldGeoOnlineTimeStats(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val regionId: Int?,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val playerFilter: UUID?,
    val totalOnlineMillis: Long,
    val totalAfkMillis: Long,
    val totalNonAfkMillis: Long,
    val playerStats: Map<UUID, WorldGeoPlayerOnlineTimeStats>
)
