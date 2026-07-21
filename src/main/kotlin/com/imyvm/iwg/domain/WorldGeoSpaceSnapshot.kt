package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier

enum class WorldGeoSpaceType {
    REGION,
    GEOSCOPE,
    SUBSPACE
}

data class WorldGeoSpaceSnapshot(
    val type: WorldGeoSpaceType,
    val id: Long,
    val name: String,
    val dimensionId: Identifier?,
    val area: Double?,
    val parentRegionId: Int?,
    val parentRegionName: String?,
    val parentScopeId: Long?,
    val parentScopeName: String?,
    val childScopeCount: Int,
    val childSubSpaceCount: Int,
    val stringTags: Set<String>,
    val keyedTags: Map<String, String>,
    val statsVersion: Long
)
