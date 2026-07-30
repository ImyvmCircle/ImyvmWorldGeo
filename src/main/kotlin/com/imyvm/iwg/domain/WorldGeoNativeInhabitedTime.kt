package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier

data class WorldGeoDimensionChunk(
    val dimensionId: Identifier,
    val chunkX: Int,
    val chunkZ: Int
)

enum class WorldGeoNativeInhabitedTimeSource {
    LOADED,
    PERSISTED
}

enum class WorldGeoNativeInhabitedTimeCompleteness {
    COMPLETE,
    CHUNK_NOT_FOUND,
    INDEX_NOT_INITIALIZED,
    CORRUPT_DATA,
    READ_FAILED,
    NEGATIVE_TICKS,
    MILLIS_OVERFLOW
}

data class WorldGeoNativeInhabitedTimeReading(
    val chunk: WorldGeoDimensionChunk,
    val inhabitedTicks: Long?,
    val inhabitedMillis: Long?,
    val collectedAtMillis: Long,
    val inputVersion: String,
    val completeness: WorldGeoNativeInhabitedTimeCompleteness,
    val source: WorldGeoNativeInhabitedTimeSource?
)

data class WorldGeoNativeInhabitedTimeBatchRequest(
    val chunks: List<WorldGeoDimensionChunk>,
    val inputVersion: String
)

data class WorldGeoNativeInhabitedTimeBatchResult(
    val readings: List<WorldGeoNativeInhabitedTimeReading>,
    val inputVersion: String
)

enum class WorldGeoSpaceGeometryStatus {
    AVAILABLE,
    NO_COVERAGE,
    CHUNK_LIMIT_EXCEEDED
}

data class WorldGeoChunkCoverage(
    val chunk: WorldGeoDimensionChunk,
    val areaRatio: Double
)

data class WorldGeoSpaceGeometryFact(
    val spaceType: WorldGeoSpaceType,
    val spaceId: Long,
    val dimensionId: Identifier?,
    val centroidChunk: WorldGeoDimensionChunk?,
    val chunkCoverage: List<WorldGeoChunkCoverage>,
    val geometryVersion: String,
    val status: WorldGeoSpaceGeometryStatus
)
