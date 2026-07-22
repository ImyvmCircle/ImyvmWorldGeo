package com.imyvm.iwg.domain

import java.util.UUID

data class WorldGeoSubSpaceTransition(
    val playerUuid: UUID,
    val playerName: String,
    val from: WorldGeoSpaceSnapshot?,
    val to: WorldGeoSpaceSnapshot?,
    val gameTimeMillis: Long
)
