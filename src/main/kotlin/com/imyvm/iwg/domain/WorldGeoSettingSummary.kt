package com.imyvm.iwg.domain

enum class WorldGeoSettingVisibility {
    PUBLIC,
    OP_DEBUG
}

data class WorldGeoSettingSummary(
    val spaceType: WorldGeoSpaceType,
    val spaceId: Long,
    val key: String,
    val value: String,
    val settingType: String,
    val subject: String
)
