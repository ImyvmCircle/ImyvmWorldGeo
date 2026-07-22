package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier

enum class WorldGeoBiomeCategory(val key: String) {
    OCEAN("ocean"),
    PLAINS("plains"),
    DESERT("desert"),
    FOREST("forest"),
    MOUNTAIN("mountain"),
    RAINFOREST("rainforest"),
    SNOWY("snowy")
}

enum class WorldGeoGeographicAttributeKind {
    BIOME_CLASS,
    COMBINED_BIOME_CLASS,
    DIVERSE,
    DIMENSION
}

enum class WorldGeoGeographicOrientation(val key: String) {
    CENTER("center"),
    EAST_AXIS("east_axis"),
    WEST_AXIS("west_axis"),
    SOUTH_AXIS("south_axis"),
    NORTH_AXIS("north_axis"),
    NORTHEAST("northeast"),
    NORTHWEST("northwest"),
    SOUTHEAST("southeast"),
    SOUTHWEST("southwest")
}

enum class WorldGeoGeographicProfileSource {
    COMPUTED,
    CACHED
}

sealed class WorldGeoGeographicProfileResult {
    data class Success(val profile: WorldGeoGeographicProfile) : WorldGeoGeographicProfileResult()
    data class ChunkLimitExceeded(
        val dimensionId: Identifier,
        val candidateChunkCount: Int,
        val limit: Int
    ) : WorldGeoGeographicProfileResult()
    data class DimensionUnavailable(val dimensionId: Identifier) : WorldGeoGeographicProfileResult()
}

data class WorldGeoBiomeCategoryRatio(
    val category: WorldGeoBiomeCategory,
    val sampleWeight: Int,
    val ratio: Double
)

data class WorldGeoGeographicProfile(
    val type: WorldGeoSpaceType,
    val id: Long,
    val name: String,
    val dimensionId: Identifier?,
    val attributeKind: WorldGeoGeographicAttributeKind,
    val attributeKey: String,
    val dominantCategories: List<WorldGeoBiomeCategory>,
    val categoryRatios: List<WorldGeoBiomeCategoryRatio>,
    val averageElevation: Double?,
    val orientation: WorldGeoGeographicOrientation?,
    val sampleWeight: Int,
    val candidateChunkCount: Int,
    val loadedChunkCount: Int,
    val rawBiomeCounts: Map<Identifier, Int>
) {
    val isPartial: Boolean
        get() = loadedChunkCount < candidateChunkCount
}


data class WorldGeoGeographicProfileSnapshot(
    val result: WorldGeoGeographicProfileResult,
    val source: WorldGeoGeographicProfileSource,
    val calculatedAtMillis: Long,
    val lastInvalidatedAtMillis: Long?,
    val lastInvalidationReason: String?
)

data class WorldGeoGeographicProfileCacheStatus(
    val cachedProfileCount: Int,
    val lastInvalidatedAtMillis: Long?,
    val lastInvalidationReason: String?
)
