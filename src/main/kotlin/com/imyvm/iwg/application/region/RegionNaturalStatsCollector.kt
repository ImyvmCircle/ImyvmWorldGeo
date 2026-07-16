package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.domain.component.BoundedShapeGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.UnknownGeometry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap

private const val COLUMNS_PER_CHUNK = 16 * 16
private const val MAX_CANDIDATE_CHUNKS = 2048
private val MAX_GEOMETRY_WORK_UNITS = MAX_CANDIDATE_CHUNKS.toLong() * COLUMNS_PER_CHUNK

internal data class NaturalStatsDimensionPlan(
    val dimensionId: Identifier,
    val scopeCount: Int,
    val candidatesByChunk: Map<Long, List<BoundedShapeGeometry>>
)

internal data class NaturalStatsCollectionPlan(
    val dimensions: List<NaturalStatsDimensionPlan>,
    val candidateChunkCount: Int,
    val geometryWorkUnits: Long
)

internal sealed interface NaturalStatsCollectionPlanResult {
    data class Ready(val plan: NaturalStatsCollectionPlan) : NaturalStatsCollectionPlanResult

    data class ChunkLimitExceeded(
        val dimensionId: Identifier,
        val candidateChunkCount: Int,
        val limit: Int
    ) : NaturalStatsCollectionPlanResult

    data class WorkLimitExceeded(
        val dimensionId: Identifier,
        val requestedWorkUnits: Long,
        val limit: Long
    ) : NaturalStatsCollectionPlanResult
}

private data class MutableDimensionPlan(
    var scopeCount: Int = 0,
    val candidatesByChunk: MutableMap<Long, MutableList<BoundedShapeGeometry>> = linkedMapOf()
)

private data class ResolvedDimensionPlan(
    val level: ServerLevel,
    val plan: NaturalStatsDimensionPlan
)

object RegionNaturalStatsCollector {
    fun collectRegionStats(server: MinecraftServer, region: Region): RegionNaturalStatsResult =
        collectRegionStatsFromScopes(server, region.scopes.toList())

    fun collectScopeStats(server: MinecraftServer, scope: GeoScope): RegionNaturalStatsResult =
        collectRegionStatsFromScopes(server, listOf(scope))

    private fun collectRegionStatsFromScopes(server: MinecraftServer, scopes: List<GeoScope>): RegionNaturalStatsResult {
        val plan = when (
            val result = buildCollectionPlan(scopes, MAX_CANDIDATE_CHUNKS, MAX_GEOMETRY_WORK_UNITS)
        ) {
            is NaturalStatsCollectionPlanResult.Ready -> result.plan
            is NaturalStatsCollectionPlanResult.ChunkLimitExceeded -> {
                return RegionNaturalStatsResult.ChunkLimitExceeded(
                    result.dimensionId,
                    result.candidateChunkCount,
                    result.limit
                )
            }
            is NaturalStatsCollectionPlanResult.WorkLimitExceeded -> {
                return RegionNaturalStatsResult.WorkLimitExceeded(
                    result.dimensionId,
                    result.requestedWorkUnits,
                    result.limit
                )
            }
        }

        val resolvedDimensions = ArrayList<ResolvedDimensionPlan>(plan.dimensions.size)
        for (dimensionPlan in plan.dimensions) {
            val dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionPlan.dimensionId)
            val level = server.getLevel(dimensionKey)
                ?: return RegionNaturalStatsResult.DimensionUnavailable(dimensionPlan.dimensionId)
            resolvedDimensions.add(ResolvedDimensionPlan(level, dimensionPlan))
        }

        val dimensionStats = linkedMapOf<Identifier, DimensionNaturalStats>()
        for ((level, dimensionPlan) in resolvedDimensions) {
            dimensionStats[dimensionPlan.dimensionId] = collectDimensionStats(level, dimensionPlan)
        }

        return RegionNaturalStatsResult.Success(
            RegionNaturalStats(
                scopeCount = scopes.size,
                candidateChunkCount = plan.candidateChunkCount,
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

    internal fun buildCollectionPlan(
        scopes: List<GeoScope>,
        candidateChunkLimit: Int = MAX_CANDIDATE_CHUNKS,
        geometryWorkLimit: Long = MAX_GEOMETRY_WORK_UNITS
    ): NaturalStatsCollectionPlanResult {
        require(candidateChunkLimit >= 0) { "candidate chunk limit must not be negative" }
        require(geometryWorkLimit >= 0) { "geometry work limit must not be negative" }

        val dimensions = linkedMapOf<Identifier, MutableDimensionPlan>()
        var candidateChunkCount = 0
        var geometryWorkUnits = 0L

        for (scope in scopes) {
            val geometry = when (val current = scope.geoShape?.typedGeometry ?: continue) {
                is BoundedShapeGeometry -> current
                UnknownGeometry -> continue
            }
            val dimension = dimensions.getOrPut(scope.worldId) { MutableDimensionPlan() }
            dimension.scopeCount++
            val bounds = geometry.bounds()
            val minChunkX = Math.floorDiv(bounds.minX, 16)
            val maxChunkX = Math.floorDiv(bounds.maxX, 16)
            val minChunkZ = Math.floorDiv(bounds.minZ, 16)
            val maxChunkZ = Math.floorDiv(bounds.maxZ, 16)
            val associationWorkUnits = Math.multiplyExact(
                COLUMNS_PER_CHUNK.toLong(),
                geometry.containmentWorkUnits.toLong()
            )

            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    val packedChunkPos = packChunkPos(chunkX, chunkZ)
                    val existingCandidates = dimension.candidatesByChunk[packedChunkPos]
                    val requestedChunkCount = candidateChunkCount + if (existingCandidates == null) 1 else 0
                    if (requestedChunkCount > candidateChunkLimit) {
                        return NaturalStatsCollectionPlanResult.ChunkLimitExceeded(
                            scope.worldId,
                            requestedChunkCount,
                            candidateChunkLimit
                        )
                    }

                    val requestedWorkUnits = Math.addExact(geometryWorkUnits, associationWorkUnits)
                    if (requestedWorkUnits > geometryWorkLimit) {
                        return NaturalStatsCollectionPlanResult.WorkLimitExceeded(
                            scope.worldId,
                            requestedWorkUnits,
                            geometryWorkLimit
                        )
                    }

                    val candidates = existingCandidates ?: mutableListOf<BoundedShapeGeometry>().also {
                        dimension.candidatesByChunk[packedChunkPos] = it
                        candidateChunkCount = requestedChunkCount
                    }
                    candidates.add(geometry)
                    geometryWorkUnits = requestedWorkUnits
                }
            }
        }

        val immutableDimensions = dimensions.entries
            .sortedBy { it.key.toString() }
            .map { (dimensionId, mutablePlan) ->
                NaturalStatsDimensionPlan(
                    dimensionId,
                    mutablePlan.scopeCount,
                    mutablePlan.candidatesByChunk.mapValuesTo(linkedMapOf()) { (_, candidates) ->
                        candidates.toList()
                    }
                )
            }
        return NaturalStatsCollectionPlanResult.Ready(
            NaturalStatsCollectionPlan(immutableDimensions, candidateChunkCount, geometryWorkUnits)
        )
    }

    private fun collectDimensionStats(
        level: ServerLevel,
        plan: NaturalStatsDimensionPlan
    ): DimensionNaturalStats {
        val structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val structureCounts = linkedMapOf<Identifier, Int>()
        val surfaceBlockCounts = linkedMapOf<Identifier, Int>()
        val biomeCounts = linkedMapOf<Identifier, Int>()
        var loadedChunkCount = 0
        var sampledColumnCount = 0
        var difficultyWeightSum = 0.0

        plan.candidatesByChunk.entries.sortedBy { it.key }.forEach { (packedChunkPos, candidateGeometries) ->
            val chunkX = ChunkPos.getX(packedChunkPos)
            val chunkZ = ChunkPos.getZ(packedChunkPos)
            val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: return@forEach
            loadedChunkCount++

            val chunkPos = ChunkPos(chunkX, chunkZ)
            var chunkSampledColumns = 0
            var difficultySamplePos: BlockPos? = null

            for (x in chunkPos.minBlockX..chunkPos.maxBlockX) {
                for (z in chunkPos.minBlockZ..chunkPos.maxBlockZ) {
                    if (!candidateGeometries.any { it.containsPoint(x, z) }) continue

                    val topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                    val surfaceY = topY - 1
                    if (surfaceY < level.minY) continue

                    val surfacePos = BlockPos(x, surfaceY, z)
                    val surfaceState = chunk.getBlockState(surfacePos)
                    if (surfaceState.isAir) continue

                    sampledColumnCount++
                    chunkSampledColumns++
                    if (difficultySamplePos == null) {
                        difficultySamplePos = BlockPos(x, topY, z)
                    }
                    incrementCount(surfaceBlockCounts, BuiltInRegistries.BLOCK.getKey(surfaceState.block))
                    incrementCount(biomeCounts, resolveBiomeId(level, surfacePos))
                }
            }

            if (chunkSampledColumns == 0) return@forEach

            val difficultyPos = requireNotNull(difficultySamplePos)
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

        return DimensionNaturalStats(
            scopeCount = plan.scopeCount,
            candidateChunkCount = plan.candidatesByChunk.size,
            loadedChunkCount = loadedChunkCount,
            sampledColumnCount = sampledColumnCount,
            averageLocalDifficulty = if (sampledColumnCount == 0) null else difficultyWeightSum / sampledColumnCount,
            structureCounts = sortCountMap(structureCounts),
            surfaceBlockCounts = sortCountMap(surfaceBlockCounts),
            biomeCounts = sortCountMap(biomeCounts)
        )
    }

    private fun packChunkPos(x: Int, z: Int): Long =
        (x.toLong() and 0xffffffffL) or ((z.toLong() and 0xffffffffL) shl 32)

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
