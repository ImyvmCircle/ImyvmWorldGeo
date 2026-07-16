package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier

sealed class RegionNaturalStatsResult {
    data class Success(val stats: RegionNaturalStats) : RegionNaturalStatsResult()

    /**
     * Rejected before world reads when the Region-wide dimension/chunk candidate total is too large.
     * [dimensionId] identifies the dimension being planned when [candidateChunkCount] crossed [limit].
     */
    data class ChunkLimitExceeded(
        val dimensionId: Identifier,
        val candidateChunkCount: Int,
        val limit: Int
    ) : RegionNaturalStatsResult()

    /**
     * Rejected before world reads when conservative Region-wide containment work is too large.
     * Work units are 256 columns multiplied by each candidate geometry's containment weight.
     */
    data class WorkLimitExceeded(
        val dimensionId: Identifier,
        val requestedWorkUnits: Long,
        val limit: Long
    ) : RegionNaturalStatsResult()

    data class DimensionUnavailable(
        val dimensionId: Identifier
    ) : RegionNaturalStatsResult()
}

data class RegionNaturalStats(
    val scopeCount: Int,
    val candidateChunkCount: Int,
    val loadedChunkCount: Int,
    val sampledColumnCount: Int,
    val averageLocalDifficulty: Double?,
    val structureCounts: Map<Identifier, Int>,
    val surfaceBlockCounts: Map<Identifier, Int>,
    val biomeCounts: Map<Identifier, Int>,
    val dimensionStats: Map<Identifier, DimensionNaturalStats>
) {
    val isPartial: Boolean
        get() = loadedChunkCount < candidateChunkCount
}

data class DimensionNaturalStats(
    val scopeCount: Int,
    val candidateChunkCount: Int,
    val loadedChunkCount: Int,
    val sampledColumnCount: Int,
    val averageLocalDifficulty: Double?,
    val structureCounts: Map<Identifier, Int>,
    val surfaceBlockCounts: Map<Identifier, Int>,
    val biomeCounts: Map<Identifier, Int>
)

enum class NaturalStatsCategory(
    val commandName: String,
    val translationSuffix: String
) {
    ALL("all", "all"),
    STRUCTURES("structures", "structures"),
    DIFFICULTY("difficulty", "difficulty"),
    SURFACE("surface", "surface"),
    BIOMES("biomes", "biomes"),
    PLAYERS("players", "players");

    companion object {
        fun fromName(name: String?): NaturalStatsCategory? {
            if (name == null) return ALL
            return entries.find { it.commandName.equals(name, ignoreCase = true) }
        }
    }
}
