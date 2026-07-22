package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoBiomeCategory
import com.imyvm.iwg.domain.WorldGeoBiomeCategoryRatio
import com.imyvm.iwg.domain.WorldGeoGeographicAttributeKind
import com.imyvm.iwg.domain.WorldGeoGeographicOrientation
import com.imyvm.iwg.domain.WorldGeoGeographicProfile
import com.imyvm.iwg.domain.WorldGeoGeographicProfileResult
import com.imyvm.iwg.domain.WorldGeoSpaceType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.util.geo.getBoundingBox
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.abs
import kotlin.math.roundToInt

object WorldGeoGeographicProfileSupport {
    private const val MAX_CANDIDATE_CHUNKS = 2048
    private const val AXIS_DISTANCE_THRESHOLD = 2000.0
    private val OVERWORLD_ID = Identifier.parse("minecraft:overworld")
    private val NETHER_ID = Identifier.parse("minecraft:the_nether")
    private val END_ID = Identifier.parse("minecraft:the_end")

    fun profile(server: MinecraftServer, region: Region): WorldGeoGeographicProfileResult {
        val scope = region.scopes
            .filter { it.geoShape != null }
            .maxByOrNull { it.geoShape?.calculateArea() ?: 0.0 }
            ?: return emptyProfile(WorldGeoSpaceType.REGION, region.numberID.toLong(), region.name, null)
        return profileForShape(
            server,
            WorldGeoSpaceType.REGION,
            region.numberID.toLong(),
            region.name,
            scope.worldId,
            requireNotNull(scope.geoShape)
        )
    }

    fun profile(server: MinecraftServer, region: Region, scope: GeoScope): WorldGeoGeographicProfileResult {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        val shape = scope.geoShape
            ?: return emptyProfile(WorldGeoSpaceType.GEOSCOPE, scope.requireAssignedScopeId().raw, scope.scopeName, scope.worldId)
        return profileForShape(server, WorldGeoSpaceType.GEOSCOPE, scope.requireAssignedScopeId().raw, scope.scopeName, scope.worldId, shape)
    }

    fun profile(server: MinecraftServer, region: Region, scope: GeoScope, subSpace: SubSpace): WorldGeoGeographicProfileResult {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        require(region.containsSubSpace(subSpace)) { "subspace does not belong to region" }
        require(subSpace.parentScopeId == scope.requireAssignedScopeId()) { "subspace parent scope does not match" }
        return profileForShape(server, WorldGeoSpaceType.SUBSPACE, subSpace.subSpaceId, subSpace.name, subSpace.worldId, subSpace.geoShape)
    }

    internal fun classifyBiome(biomeId: Identifier): WorldGeoBiomeCategory {
        val path = biomeId.path
        return when {
            path.contains("ocean") || path.contains("river") || path.contains("beach") || path.contains("shore") -> WorldGeoBiomeCategory.OCEAN
            path.contains("snow") || path.contains("frozen") || path.contains("ice") -> WorldGeoBiomeCategory.SNOWY
            path.contains("jungle") || path.contains("mangrove") || path.contains("swamp") -> WorldGeoBiomeCategory.RAINFOREST
            path.contains("desert") || path.contains("badlands") -> WorldGeoBiomeCategory.DESERT
            path.contains("mountain") || path.contains("peak") || path.contains("slope") || path.contains("grove") || path.contains("meadow") || path.contains("windswept") -> WorldGeoBiomeCategory.MOUNTAIN
            path.contains("forest") || path.contains("taiga") || path.contains("birch") || path.contains("cherry") -> WorldGeoBiomeCategory.FOREST
            else -> WorldGeoBiomeCategory.PLAINS
        }
    }

    internal fun resolveAttribute(categoryWeights: Map<WorldGeoBiomeCategory, Int>): Pair<WorldGeoGeographicAttributeKind, List<WorldGeoBiomeCategory>> {
        val total = categoryWeights.values.sum()
        if (total <= 0) return WorldGeoGeographicAttributeKind.DIVERSE to emptyList()
        val sorted = categoryWeights.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<WorldGeoBiomeCategory, Int>> { it.value }.thenBy { it.key.ordinal })
        val first = sorted.first()
        if (first.key == WorldGeoBiomeCategory.OCEAN) return WorldGeoGeographicAttributeKind.BIOME_CLASS to listOf(first.key)
        val land = sorted.filter { it.key != WorldGeoBiomeCategory.OCEAN }
        if (land.isEmpty()) return WorldGeoGeographicAttributeKind.BIOME_CLASS to listOf(WorldGeoBiomeCategory.OCEAN)
        val top = land.first()
        if (top.value.toDouble() / total >= 0.5) return WorldGeoGeographicAttributeKind.BIOME_CLASS to listOf(top.key)
        val topTwo = land.take(2)
        if (topTwo.size == 2 && topTwo.sumOf { it.value }.toDouble() / total >= 0.7) {
            return WorldGeoGeographicAttributeKind.COMBINED_BIOME_CLASS to topTwo.map { it.key }
        }
        return WorldGeoGeographicAttributeKind.DIVERSE to emptyList()
    }

    internal fun orientation(shape: GeoShape): WorldGeoGeographicOrientation {
        val (x, z) = centroid(shape)
        val nearZAxis = abs(x) <= AXIS_DISTANCE_THRESHOLD
        val nearXAxis = abs(z) <= AXIS_DISTANCE_THRESHOLD
        return when {
            nearZAxis && nearXAxis -> WorldGeoGeographicOrientation.CENTER
            nearZAxis && z < 0.0 -> WorldGeoGeographicOrientation.NORTH_AXIS
            nearZAxis -> WorldGeoGeographicOrientation.SOUTH_AXIS
            nearXAxis && x < 0.0 -> WorldGeoGeographicOrientation.WEST_AXIS
            nearXAxis -> WorldGeoGeographicOrientation.EAST_AXIS
            x >= 0.0 && z < 0.0 -> WorldGeoGeographicOrientation.NORTHEAST
            x < 0.0 && z < 0.0 -> WorldGeoGeographicOrientation.NORTHWEST
            x >= 0.0 -> WorldGeoGeographicOrientation.SOUTHEAST
            else -> WorldGeoGeographicOrientation.SOUTHWEST
        }
    }

    private fun profileForShape(
        server: MinecraftServer,
        type: WorldGeoSpaceType,
        id: Long,
        name: String,
        dimensionId: Identifier,
        shape: GeoShape
    ): WorldGeoGeographicProfileResult {
        if (dimensionId == NETHER_ID || dimensionId == END_ID) {
            return WorldGeoGeographicProfileResult.Success(
                baseProfile(type, id, name, dimensionId, dimensionAttributeKey(dimensionId), WorldGeoGeographicAttributeKind.DIMENSION, emptyList(), emptyList(), null, null, 0, 0, 0, emptyMap())
            )
        }
        if (dimensionId != OVERWORLD_ID) {
            return WorldGeoGeographicProfileResult.Success(
                baseProfile(type, id, name, dimensionId, dimensionId.toString(), WorldGeoGeographicAttributeKind.DIMENSION, emptyList(), emptyList(), null, null, 0, 0, 0, emptyMap())
            )
        }
        val level = GeoScope("profileSample", dimensionId, null, geoShape = shape).getWorld(server)
            ?: return WorldGeoGeographicProfileResult.DimensionUnavailable(dimensionId)
        return collectOverworldProfile(level, type, id, name, shape)
    }

    private fun collectOverworldProfile(
        level: ServerLevel,
        type: WorldGeoSpaceType,
        id: Long,
        name: String,
        shape: GeoShape
    ): WorldGeoGeographicProfileResult {
        val candidateChunks = collectCandidateChunks(shape)
            ?: return WorldGeoGeographicProfileResult.ChunkLimitExceeded(level.dimension().identifier(), MAX_CANDIDATE_CHUNKS + 1, MAX_CANDIDATE_CHUNKS)
        val categoryWeights = linkedMapOf<WorldGeoBiomeCategory, Int>()
        val rawBiomeCounts = linkedMapOf<Identifier, Int>()
        var loadedChunkCount = 0
        var sampleWeight = 0
        var elevationWeightSum = 0.0

        candidateChunks.sorted().forEach { packedChunkPos ->
            val chunkX = ChunkPos.getX(packedChunkPos)
            val chunkZ = ChunkPos.getZ(packedChunkPos)
            val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: return@forEach
            loadedChunkCount++
            val sample = representativePointInChunk(shape, ChunkPos(chunkX, chunkZ)) ?: return@forEach
            val topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, sample.first, sample.second)
            val surfaceY = topY - 1
            if (surfaceY < level.minY) return@forEach
            val weight = sample.third
            val surfacePos = BlockPos(sample.first, surfaceY, sample.second)
            val biomeId = resolveBiomeId(level, surfacePos)
            val category = classifyBiome(biomeId)
            categoryWeights[category] = (categoryWeights[category] ?: 0) + weight
            rawBiomeCounts[biomeId] = (rawBiomeCounts[biomeId] ?: 0) + weight
            sampleWeight += weight
            elevationWeightSum += surfaceY.toDouble() * weight
        }

        val (attributeKind, dominantCategories) = resolveAttribute(categoryWeights)
        val ratios = categoryWeights.entries
            .sortedWith(compareByDescending<Map.Entry<WorldGeoBiomeCategory, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { WorldGeoBiomeCategoryRatio(it.key, it.value, if (sampleWeight == 0) 0.0 else it.value.toDouble() / sampleWeight) }
        val rawBiomes = rawBiomeCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Identifier, Int>> { it.value }.thenBy { it.key.toString() })
            .associateTo(linkedMapOf()) { it.toPair() }
        return WorldGeoGeographicProfileResult.Success(
            baseProfile(
                type,
                id,
                name,
                level.dimension().identifier(),
                attributeKey(attributeKind, dominantCategories),
                attributeKind,
                dominantCategories,
                ratios,
                if (sampleWeight == 0) null else elevationWeightSum / sampleWeight,
                orientation(shape),
                sampleWeight,
                candidateChunks.size,
                loadedChunkCount,
                rawBiomes
            )
        )
    }

    private fun baseProfile(
        type: WorldGeoSpaceType,
        id: Long,
        name: String,
        dimensionId: Identifier?,
        attributeKey: String,
        attributeKind: WorldGeoGeographicAttributeKind,
        dominantCategories: List<WorldGeoBiomeCategory>,
        ratios: List<WorldGeoBiomeCategoryRatio>,
        averageElevation: Double?,
        orientation: WorldGeoGeographicOrientation?,
        sampleWeight: Int,
        candidateChunkCount: Int,
        loadedChunkCount: Int,
        rawBiomeCounts: Map<Identifier, Int>
    ): WorldGeoGeographicProfile = WorldGeoGeographicProfile(
        type = type,
        id = id,
        name = name,
        dimensionId = dimensionId,
        attributeKind = attributeKind,
        attributeKey = attributeKey,
        dominantCategories = dominantCategories,
        categoryRatios = ratios,
        averageElevation = averageElevation,
        orientation = orientation,
        sampleWeight = sampleWeight,
        candidateChunkCount = candidateChunkCount,
        loadedChunkCount = loadedChunkCount,
        rawBiomeCounts = rawBiomeCounts
    )

    private fun emptyProfile(type: WorldGeoSpaceType, id: Long, name: String, dimensionId: Identifier?): WorldGeoGeographicProfileResult =
        WorldGeoGeographicProfileResult.Success(
            baseProfile(type, id, name, dimensionId, "diverse", WorldGeoGeographicAttributeKind.DIVERSE, emptyList(), emptyList(), null, null, 0, 0, 0, emptyMap())
        )

    private fun dimensionAttributeKey(dimensionId: Identifier): String = when (dimensionId) {
        NETHER_ID -> "nether"
        END_ID -> "end"
        else -> dimensionId.toString()
    }

    private fun attributeKey(kind: WorldGeoGeographicAttributeKind, categories: List<WorldGeoBiomeCategory>): String = when (kind) {
        WorldGeoGeographicAttributeKind.BIOME_CLASS -> categories.singleOrNull()?.key ?: "diverse"
        WorldGeoGeographicAttributeKind.COMBINED_BIOME_CLASS -> categories.joinToString("+") { it.key }
        WorldGeoGeographicAttributeKind.DIVERSE -> "diverse"
        WorldGeoGeographicAttributeKind.DIMENSION -> "dimension"
    }

    private fun collectCandidateChunks(shape: GeoShape): Set<Long>? {
        val (minX, minZ, maxX, maxZ) = getShapeBoundingBox(shape)
        val candidateChunks = linkedSetOf<Long>()
        val minChunkX = Math.floorDiv(minX, 16)
        val maxChunkX = Math.floorDiv(maxX, 16)
        val minChunkZ = Math.floorDiv(minZ, 16)
        val maxChunkZ = Math.floorDiv(maxZ, 16)
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (candidateChunks.size >= MAX_CANDIDATE_CHUNKS) return null
                candidateChunks.add(packChunkPos(chunkX, chunkZ))
            }
        }
        return candidateChunks
    }

    private fun representativePointInChunk(shape: GeoShape, chunkPos: ChunkPos): Triple<Int, Int, Int>? {
        var count = 0
        var sampleX = 0
        var sampleZ = 0
        var bestHash = Long.MAX_VALUE
        for (x in chunkPos.minBlockX..chunkPos.maxBlockX) {
            for (z in chunkPos.minBlockZ..chunkPos.maxBlockZ) {
                if (!shape.containsPoint(x, z)) continue
                count++
                val hash = sampleHash(x, z)
                if (hash < bestHash) {
                    bestHash = hash
                    sampleX = x
                    sampleZ = z
                }
            }
        }
        return if (count == 0) null else Triple(sampleX, sampleZ, count)
    }

    private fun getShapeBoundingBox(shape: GeoShape): IntArray {
        shape.validateParameters()
        val params = shape.shapeParameter
        return when (shape.geoShapeType) {
            GeoShapeType.CIRCLE -> intArrayOf(params[0] - params[2], params[1] - params[2], params[0] + params[2], params[1] + params[2])
            GeoShapeType.RECTANGLE -> intArrayOf(minOf(params[0], params[2]), minOf(params[1], params[3]), maxOf(params[0], params[2]), maxOf(params[1], params[3]))
            GeoShapeType.POLYGON -> getBoundingBox(params)
            GeoShapeType.UNKNOWN -> error("unknown shape has no bounding box")
        }
    }

    private fun centroid(shape: GeoShape): Pair<Double, Double> {
        val params = shape.shapeParameter
        return when (shape.geoShapeType) {
            GeoShapeType.CIRCLE -> params[0].toDouble() to params[1].toDouble()
            GeoShapeType.RECTANGLE -> ((params[0] + params[2]).toDouble() / 2.0) to ((params[1] + params[3]).toDouble() / 2.0)
            GeoShapeType.POLYGON -> polygonCentroid(params)
            GeoShapeType.UNKNOWN -> 0.0 to 0.0
        }
    }

    private fun polygonCentroid(params: List<Int>): Pair<Double, Double> {
        var twiceArea = 0.0
        var xSum = 0.0
        var zSum = 0.0
        val count = params.size / 2
        for (index in 0 until count) {
            val next = (index + 1) % count
            val x0 = params[index * 2].toDouble()
            val z0 = params[index * 2 + 1].toDouble()
            val x1 = params[next * 2].toDouble()
            val z1 = params[next * 2 + 1].toDouble()
            val cross = x0 * z1 - x1 * z0
            twiceArea += cross
            xSum += (x0 + x1) * cross
            zSum += (z0 + z1) * cross
        }
        if (twiceArea == 0.0) {
            val xs = (0 until count).map { params[it * 2] }
            val zs = (0 until count).map { params[it * 2 + 1] }
            return xs.average() to zs.average()
        }
        return (xSum / (3.0 * twiceArea)) to (zSum / (3.0 * twiceArea))
    }

    private fun resolveBiomeId(level: ServerLevel, pos: BlockPos): Identifier {
        val biomeKey = level.getBiome(pos).unwrapKey().orElse(null)
        return requireNotNull(biomeKey) { "Unbound biome registry entry at $pos." }.identifier()
    }

    private fun sampleHash(x: Int, z: Int): Long {
        var value = x.toLong() * 341873128712L + z.toLong() * 132897987541L
        value = value xor (value ushr 33)
        value *= -4417276706812531889L
        value = value xor (value ushr 29)
        return value and Long.MAX_VALUE
    }

    private fun packChunkPos(x: Int, z: Int): Long =
        (x.toLong() and 0xffffffffL) or ((z.toLong() and 0xffffffffL) shl 32)
}
