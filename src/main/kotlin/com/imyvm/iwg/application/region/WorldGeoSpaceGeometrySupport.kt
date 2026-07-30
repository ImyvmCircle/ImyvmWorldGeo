package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoChunkCoverage
import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryFact
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryStatus
import com.imyvm.iwg.domain.WorldGeoSpaceType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.SubSpace
import net.minecraft.resources.Identifier
import java.security.MessageDigest
import kotlin.math.floor

internal object WorldGeoSpaceGeometrySupport {
    const val MAX_CHUNKS = 4_096

    fun region(region: Region): List<WorldGeoSpaceGeometryFact> {
        if (region.scopes.isEmpty()) {
            return listOf(
                fact(
                    WorldGeoSpaceType.REGION,
                    region.numberID.toLong(),
                    null,
                    emptyList()
                )
            )
        }
        return region.scopes
            .groupBy { it.worldId }
            .toSortedMap(compareBy(Identifier::toString))
            .map { (dimensionId, scopes) ->
                fact(
                    WorldGeoSpaceType.REGION,
                    region.numberID.toLong(),
                    dimensionId,
                    scopes.mapNotNull { it.geoShape }
                )
            }
    }

    fun scope(scope: GeoScope): WorldGeoSpaceGeometryFact =
        fact(
            WorldGeoSpaceType.GEOSCOPE,
            scope.requireAssignedScopeId().raw,
            scope.worldId,
            listOfNotNull(scope.geoShape)
        )

    fun subSpace(subSpace: SubSpace): WorldGeoSpaceGeometryFact =
        fact(
            WorldGeoSpaceType.SUBSPACE,
            subSpace.subSpaceId,
            subSpace.worldId,
            listOf(subSpace.geoShape)
        )

    fun coveredChunks(facts: List<WorldGeoSpaceGeometryFact>): List<WorldGeoDimensionChunk> =
        facts
            .filter { it.status == WorldGeoSpaceGeometryStatus.AVAILABLE }
            .flatMap { it.chunkCoverage }
            .map { it.chunk }
            .distinct()

    private fun fact(
        type: WorldGeoSpaceType,
        id: Long,
        dimensionId: Identifier?,
        shapes: List<GeoShape>
    ): WorldGeoSpaceGeometryFact {
        val version = geometryVersion(type, id, dimensionId, shapes)
        if (dimensionId == null || shapes.isEmpty()) {
            return WorldGeoSpaceGeometryFact(
                type,
                id,
                dimensionId,
                null,
                emptyList(),
                version,
                WorldGeoSpaceGeometryStatus.NO_COVERAGE
            )
        }
        val candidateChunks = linkedSetOf<Long>()
        for (shape in shapes) {
            val bounds = bounds(shape)
            val minChunkX = Math.floorDiv(bounds[0], 16)
            val minChunkZ = Math.floorDiv(bounds[1], 16)
            val maxChunkX = Math.floorDiv(bounds[2], 16)
            val maxChunkZ = Math.floorDiv(bounds[3], 16)
            val width = maxChunkX.toLong() - minChunkX + 1L
            val depth = maxChunkZ.toLong() - minChunkZ + 1L
            if (width * depth > MAX_CHUNKS ||
                !RegionNaturalStatsCollector.addChunkRangeWithinLimit(
                    candidateChunks,
                    minChunkX,
                    maxChunkX,
                    minChunkZ,
                    maxChunkZ,
                    MAX_CHUNKS
                )
            ) {
                return WorldGeoSpaceGeometryFact(
                    type,
                    id,
                    dimensionId,
                    null,
                    emptyList(),
                    version,
                    WorldGeoSpaceGeometryStatus.CHUNK_LIMIT_EXCEEDED
                )
            }
        }
        val coveredColumns = linkedMapOf<Long, Int>()
        var weightedX = 0.0
        var weightedZ = 0.0
        candidateChunks.sorted().forEach { packed ->
            val chunkX = packed.toInt()
            val chunkZ = (packed shr 32).toInt()
            var count = 0
            val minX = chunkX * 16
            val minZ = chunkZ * 16
            for (xOffset in 0 until 16) {
                for (zOffset in 0 until 16) {
                    val x = minX + xOffset
                    val z = minZ + zOffset
                    if (shapes.any { it.containsPoint(x, z) }) {
                        count++
                        weightedX += x
                        weightedZ += z
                    }
                }
            }
            if (count > 0) coveredColumns[packed] = count
        }
        if (coveredColumns.isEmpty()) {
            return WorldGeoSpaceGeometryFact(
                type,
                id,
                dimensionId,
                null,
                emptyList(),
                version,
                WorldGeoSpaceGeometryStatus.NO_COVERAGE
            )
        }
        val totalColumns = coveredColumns.values.sum()
        val centroidX = floor(weightedX / totalColumns).toInt()
        val centroidZ = floor(weightedZ / totalColumns).toInt()
        val centroid = WorldGeoDimensionChunk(
            dimensionId,
            Math.floorDiv(centroidX, 16),
            Math.floorDiv(centroidZ, 16)
        )
        val coverage = coveredColumns.map { (packed, count) ->
            WorldGeoChunkCoverage(
                WorldGeoDimensionChunk(dimensionId, packed.toInt(), (packed shr 32).toInt()),
                count / 256.0
            )
        }
        return WorldGeoSpaceGeometryFact(
            type,
            id,
            dimensionId,
            centroid,
            coverage,
            version,
            WorldGeoSpaceGeometryStatus.AVAILABLE
        )
    }

    private fun bounds(shape: GeoShape): IntArray {
        val parameters = shape.shapeParameter
        return when (shape.geoShapeType) {
            GeoShapeType.CIRCLE -> intArrayOf(
                Math.subtractExact(parameters[0], parameters[2]),
                Math.subtractExact(parameters[1], parameters[2]),
                Math.addExact(parameters[0], parameters[2]),
                Math.addExact(parameters[1], parameters[2])
            )
            GeoShapeType.RECTANGLE -> parameters.toIntArray()
            GeoShapeType.POLYGON -> {
                val xs = parameters.filterIndexed { index, _ -> index % 2 == 0 }
                val zs = parameters.filterIndexed { index, _ -> index % 2 == 1 }
                intArrayOf(xs.min(), zs.min(), xs.max(), zs.max())
            }
            GeoShapeType.UNKNOWN -> error("unknown shape has no coverage")
        }
    }

    private fun geometryVersion(
        type: WorldGeoSpaceType,
        id: Long,
        dimensionId: Identifier?,
        shapes: List<GeoShape>
    ): String {
        val stable = buildString {
            append(type.name).append('|').append(id).append('|').append(dimensionId).append('|')
            shapes.map { "${it.geoShapeType.name}:${it.shapeParameter.joinToString(",")}" }
                .sorted()
                .forEach { append(it).append('|') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(stable.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
