package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoShapeType
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
    val statsVersion: Long,
    val dominantBiomeId: Identifier? = null,
    val entryMessageEnabled: Boolean = true,
    val entryMessageConfigured: Boolean = false,
    val mapColorSuggestion: Int? = null,
    val settingSummary: List<WorldGeoSettingSummary> = emptyList(),
    val displayName: String = name,
    val shapeType: GeoShapeType? = null,
    val shapeParameters: List<Int> = emptyList()
)
