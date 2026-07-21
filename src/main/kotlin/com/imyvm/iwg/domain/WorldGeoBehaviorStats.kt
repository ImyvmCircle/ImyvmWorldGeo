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
    val objectId: String? = null
)

data class WorldGeoBehaviorStatsEntry(
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val behaviorType: WorldGeoBehaviorType,
    val regionId: Int,
    val scopeId: Long?,
    val subSpaceId: Long?,
    val playerUuid: UUID,
    val objectId: String?,
    val count: Long
)
