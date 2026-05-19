package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.geo.getBoundingBox
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap

object RegionNaturalStatsCollector {
    private const val MAX_CANDIDATE_CHUNKS = 2048

    fun collectRegionStats(server: MinecraftServer, region: Region): RegionNaturalStatsResult =
        collectRegionStatsFromScopes(server, region.geometryScope)

    fun collectScopeStats(server: MinecraftServer, scope: GeoScope): RegionNaturalStatsResult =
        collectRegionStatsFromScopes(server, listOf(scope))

    private fun collectRegionStatsFromScopes(server: MinecraftServer, scopes: List<GeoScope>): RegionNaturalStatsResult {
        val dimensionEntries = scopes
            .filter { it.geoShape != null }
            .groupBy { it.worldId }
            .entries
            .sortedBy { it.key.toString() }

        val dimensionStats = linkedMapOf<Identifier, DimensionNaturalStats>()
        for ((dimensionId, groupedScopes) in dimensionEntries) {
            val level = groupedScopes.first().getWorld(server)
                ?: return RegionNaturalStatsResult.DimensionUnavailable(dimensionId)
            val dimensionResult = collectDimensionStats(level, groupedScopes)
            if (dimensionResult is RegionNaturalStatsResult.ChunkLimitExceeded) {
                return dimensionResult
            }
            val stats = (dimensionResult as RegionNaturalStatsResult.Success).stats
            dimensionStats[dimensionId] = requireNotNull(stats.dimensionStats[dimensionId])
        }

        return RegionNaturalStatsResult.Success(
            RegionNaturalStats(
                scopeCount = scopes.size,
                candidateChunkCount = dimensionStats.values.sumOf { it.candidateChunkCount },
                loadedChunkCount = dimensionStats.values.sumOf { it.loadedChunkCount },
                sampledColumnCount = dimensionStats.values.sumOf { it.sampledColumnCount },
                averageLocalDifficulty = aggregateDifficulty(dimensionStats.values.toList()),
                structureCounts = mergeAndSortMaps(dimensionStats.values.map { it.structureCounts }),
                surfaceBlockCounts = mergeAndSortMaps(dimensionStats.values.map { it.surfaceBlockCounts }),
                biomeCounts = mergeAndSortMaps(dimensionStats.values.map { it.biomeCounts }),
                dimensionStats = dimensionStats
            )
        )
    }

    private fun collectDimensionStats(level: ServerLevel, scopes: List<GeoScope>): RegionNaturalStatsResult {
        val candidateChunks = collectCandidateChunks(scopes)
        if (candidateChunks.size > MAX_CANDIDATE_CHUNKS) {
            return RegionNaturalStatsResult.ChunkLimitExceeded(
                level.dimension().identifier(),
                candidateChunks.size,
                MAX_CANDIDATE_CHUNKS
            )
        }

        val structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val structureCounts = linkedMapOf<Identifier, Int>()
        val surfaceBlockCounts = linkedMapOf<Identifier, Int>()
        val biomeCounts = linkedMapOf<Identifier, Int>()
        var loadedChunkCount = 0
        var sampledColumnCount = 0
        var difficultyWeightSum = 0.0

        candidateChunks.sorted().forEach { packedChunkPos ->
            val chunkX = ChunkPos.getX(packedChunkPos)
            val chunkZ = ChunkPos.getZ(packedChunkPos)
            val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: return@forEach
            loadedChunkCount++

            val chunkPos = ChunkPos(chunkX, chunkZ)
            var chunkSampledColumns = 0

            for (x in chunkPos.minBlockX..chunkPos.maxBlockX) {
                for (z in chunkPos.minBlockZ..chunkPos.maxBlockZ) {
                    if (!containsAnyScope(scopes, x, z)) continue

                    val topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                    val surfaceY = topY - 1
                    if (surfaceY < level.minY) continue

                    val surfacePos = BlockPos(x, surfaceY, z)
                    val surfaceState = chunk.getBlockState(surfacePos)
                    if (surfaceState.isAir) continue

                    sampledColumnCount++
                    chunkSampledColumns++
                    incrementCount(surfaceBlockCounts, BuiltInRegistries.BLOCK.getKey(surfaceState.block))
                    incrementCount(biomeCounts, resolveBiomeId(level, surfacePos))
                }
            }

            if (chunkSampledColumns == 0) return@forEach

            val difficultyY = level.getHeight(Heightmap.Types.WORLD_SURFACE, chunkPos.middleBlockX, chunkPos.middleBlockZ)
            val difficultyPos = BlockPos(chunkPos.middleBlockX, difficultyY, chunkPos.middleBlockZ)
            val difficulty = level.getCurrentDifficultyAt(difficultyPos).effectiveDifficulty.toDouble()
            difficultyWeightSum += difficulty * chunkSampledColumns

            chunk.allStarts.entries
                .asSequence()
                .filter { (_, start) -> start.isValid && start.chunkPos == chunkPos }
                .sortedBy { (structure, _) ->
                    requireNotNull(structureRegistry.getKey(structure)) { "Unbound structure registry entry." }.toString()
                }
                .forEach { (structure, _) ->
                    incrementCount(
                        structureCounts,
                        requireNotNull(structureRegistry.getKey(structure)) { "Unbound structure registry entry." }
                    )
                }
        }

        val dimensionId = level.dimension().identifier()
        val dimensionStats = DimensionNaturalStats(
            scopeCount = scopes.size,
            candidateChunkCount = candidateChunks.size,
            loadedChunkCount = loadedChunkCount,
            sampledColumnCount = sampledColumnCount,
            averageLocalDifficulty = if (sampledColumnCount == 0) null else difficultyWeightSum / sampledColumnCount,
            structureCounts = sortCountMap(structureCounts),
            surfaceBlockCounts = sortCountMap(surfaceBlockCounts),
            biomeCounts = sortCountMap(biomeCounts)
        )

        return RegionNaturalStatsResult.Success(
            RegionNaturalStats(
                scopeCount = scopes.size,
                candidateChunkCount = dimensionStats.candidateChunkCount,
                loadedChunkCount = dimensionStats.loadedChunkCount,
                sampledColumnCount = dimensionStats.sampledColumnCount,
                averageLocalDifficulty = dimensionStats.averageLocalDifficulty,
                structureCounts = dimensionStats.structureCounts,
                surfaceBlockCounts = dimensionStats.surfaceBlockCounts,
                biomeCounts = dimensionStats.biomeCounts,
                dimensionStats = linkedMapOf(dimensionId to dimensionStats)
            )
        )
    }

    private fun collectCandidateChunks(scopes: List<GeoScope>): Set<Long> {
        val candidateChunks = linkedSetOf<Long>()
        scopes.mapNotNull { it.geoShape }
            .forEach { shape ->
                val (minX, minZ, maxX, maxZ) = getShapeBoundingBox(shape)
                val minChunkX = Math.floorDiv(minX, 16)
                val maxChunkX = Math.floorDiv(maxX, 16)
                val minChunkZ = Math.floorDiv(minZ, 16)
                val maxChunkZ = Math.floorDiv(maxZ, 16)
                for (chunkX in minChunkX..maxChunkX) {
                    for (chunkZ in minChunkZ..maxChunkZ) {
                        candidateChunks.add(ChunkPos.pack(chunkX, chunkZ))
                    }
                }
            }
        return candidateChunks
    }

    private fun getShapeBoundingBox(shape: GeoShape): IntArray {
        val params = shape.shapeParameter
        return when (shape.geoShapeType) {
            GeoShapeType.CIRCLE -> {
                val centerX = params.getOrNull(0) ?: 0
                val centerZ = params.getOrNull(1) ?: 0
                val radius = params.getOrNull(2) ?: 0
                intArrayOf(centerX - radius, centerZ - radius, centerX + radius, centerZ + radius)
            }

            GeoShapeType.RECTANGLE -> {
                val west = minOf(params.getOrNull(0) ?: 0, params.getOrNull(2) ?: 0)
                val north = minOf(params.getOrNull(1) ?: 0, params.getOrNull(3) ?: 0)
                val east = maxOf(params.getOrNull(0) ?: 0, params.getOrNull(2) ?: 0)
                val south = maxOf(params.getOrNull(1) ?: 0, params.getOrNull(3) ?: 0)
                intArrayOf(west, north, east, south)
            }

            GeoShapeType.POLYGON -> getBoundingBox(params)
            GeoShapeType.UNKNOWN -> intArrayOf(0, 0, -1, -1)
        }
    }

    private fun containsAnyScope(scopes: List<GeoScope>, x: Int, z: Int): Boolean =
        scopes.any { scope -> scope.geoShape?.containsPoint(x, z) == true }

    private fun resolveBiomeId(level: ServerLevel, pos: BlockPos): Identifier {
        val biomeKey = level.getBiome(pos).unwrapKey().orElse(null)
        return requireNotNull(biomeKey) { "Unbound biome registry entry at $pos." }.identifier()
    }

    private fun incrementCount(target: MutableMap<Identifier, Int>, key: Identifier) {
        target[key] = (target[key] ?: 0) + 1
    }

    private fun mergeAndSortMaps(maps: List<Map<Identifier, Int>>): Map<Identifier, Int> {
        val merged = linkedMapOf<Identifier, Int>()
        maps.forEach { current ->
            current.forEach { (key, value) ->
                merged[key] = (merged[key] ?: 0) + value
            }
        }
        return sortCountMap(merged)
    }

    private fun sortCountMap(source: Map<Identifier, Int>): Map<Identifier, Int> =
        source.entries
            .sortedWith(compareByDescending<Map.Entry<Identifier, Int>> { it.value }.thenBy { it.key.toString() })
            .associateTo(linkedMapOf()) { it.toPair() }

    private fun aggregateDifficulty(dimensionStats: List<DimensionNaturalStats>): Double? {
        val sampledColumns = dimensionStats.sumOf { it.sampledColumnCount }
        if (sampledColumns == 0) return null

        val weightedDifficulty = dimensionStats.sumOf { (it.averageLocalDifficulty ?: 0.0) * it.sampledColumnCount }
        return weightedDifficulty / sampledColumns
    }
}
