package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegionNaturalStatsCollectorTest {
    @Test
    fun `single rectangle allows exact region wide chunk and work limits`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(scope("minecraft:overworld", rectangle(0, 0, 2_047, 255)))
        )

        val plan = assertIs<NaturalStatsCollectionPlanResult.Ready>(result).plan
        assertEquals(2_048, plan.candidateChunkCount)
        assertEquals(524_288L, plan.geometryWorkUnits)
        assertEquals(2_048, plan.dimensions.single().candidatesByChunk.size)
    }

    @Test
    fun `region wide chunk limit rejects the first candidate beyond limit`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(scope("minecraft:overworld", rectangle(0, 0, 10_927, 47)))
        )

        val exceeded = assertIs<NaturalStatsCollectionPlanResult.ChunkLimitExceeded>(result)
        assertEquals(Identifier.parse("minecraft:overworld"), exceeded.dimensionId)
        assertEquals(2_049, exceeded.candidateChunkCount)
        assertEquals(2_048, exceeded.limit)
    }

    @Test
    fun `candidate chunk budget is shared across dimensions`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(
                scope("minecraft:overworld", rectangle(0, 0, 1_023, 255)),
                scope("minecraft:the_nether", rectangle(0, 0, 655, 399))
            )
        )

        val exceeded = assertIs<NaturalStatsCollectionPlanResult.ChunkLimitExceeded>(result)
        assertEquals(Identifier.parse("minecraft:the_nether"), exceeded.dimensionId)
        assertEquals(2_049, exceeded.candidateChunkCount)
    }

    @Test
    fun `overlapping shapes consume association work without duplicating chunks`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(
                scope("minecraft:overworld", rectangle(0, 0, 15, 15)),
                scope("minecraft:overworld", rectangle(0, 0, 15, 15))
            ),
            candidateChunkLimit = 1,
            geometryWorkLimit = 256
        )

        val exceeded = assertIs<NaturalStatsCollectionPlanResult.WorkLimitExceeded>(result)
        assertEquals(512L, exceeded.requestedWorkUnits)
        assertEquals(256L, exceeded.limit)
    }

    @Test
    fun `polygon work is weighted by vertex count`() {
        val triangle = scope(
            "minecraft:overworld",
            GeoShape.polygon(listOf(GeoPoint(0, 0), GeoPoint(15, 0), GeoPoint(0, 15)))
        )
        val accepted = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(triangle),
            candidateChunkLimit = 1,
            geometryWorkLimit = 768
        )
        val rejected = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(triangle),
            candidateChunkLimit = 1,
            geometryWorkLimit = 767
        )

        assertEquals(768L, assertIs<NaturalStatsCollectionPlanResult.Ready>(accepted).plan.geometryWorkUnits)
        assertEquals(768L, assertIs<NaturalStatsCollectionPlanResult.WorkLimitExceeded>(rejected).requestedWorkUnits)
    }

    @Test
    fun `maximum polygon charges work before candidate allocation`() {
        val polygon = GeoShape(GeoShapeType.POLYGON, squarePolygonParameters())
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(scope("minecraft:overworld", polygon)),
            candidateChunkLimit = 100,
            geometryWorkLimit = 65_535
        )

        val exceeded = assertIs<NaturalStatsCollectionPlanResult.WorkLimitExceeded>(result)
        assertEquals(65_536L, exceeded.requestedWorkUnits)
    }

    @Test
    fun `candidate index contains only geometries whose bounds reach each chunk`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(
                scope("minecraft:overworld", rectangle(0, 0, 15, 15)),
                scope("minecraft:overworld", rectangle(32, 0, 47, 15))
            ),
            candidateChunkLimit = 2,
            geometryWorkLimit = 512
        )

        val dimension = assertIs<NaturalStatsCollectionPlanResult.Ready>(result).plan.dimensions.single()
        assertEquals(2, dimension.candidatesByChunk.size)
        assertTrue(dimension.candidatesByChunk.values.all { it.size == 1 })
    }

    @Test
    fun `unknown geometry is excluded and ready dimensions use stable order`() {
        val unknown = GeoShape(GeoShapeType.UNKNOWN, mutableListOf())
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(
                scope("minecraft:the_nether", rectangle(0, 0, 15, 15)),
                scope("minecraft:overworld", unknown),
                scope("minecraft:the_end", rectangle(0, 0, 15, 15))
            ),
            candidateChunkLimit = 2,
            geometryWorkLimit = 512
        )

        val plan = assertIs<NaturalStatsCollectionPlanResult.Ready>(result).plan
        assertEquals(
            listOf(Identifier.parse("minecraft:the_end"), Identifier.parse("minecraft:the_nether")),
            plan.dimensions.map { it.dimensionId }
        )
        assertEquals(2, plan.candidateChunkCount)
        assertEquals(512L, plan.geometryWorkUnits)
    }

    @Test
    fun `extreme coordinate range stops at the first over budget candidate`() {
        val result = RegionNaturalStatsCollector.buildCollectionPlan(
            listOf(scope("minecraft:overworld", rectangle(Int.MIN_VALUE, 0, Int.MAX_VALUE, 0))),
            candidateChunkLimit = 32,
            geometryWorkLimit = Int.MAX_VALUE.toLong()
        )

        val exceeded = assertIs<NaturalStatsCollectionPlanResult.ChunkLimitExceeded>(result)
        assertEquals(33, exceeded.candidateChunkCount)
    }

    private fun rectangle(west: Int, north: Int, east: Int, south: Int): GeoShape =
        GeoShape.rectangle(GeoPoint(west, north), GeoPoint(east, south))

    private fun scope(worldId: String, shape: GeoShape): GeoScope = GeoScope(
        "scope",
        Identifier.parse(worldId),
        null,
        geoShape = shape
    )

    private fun squarePolygonParameters(): MutableList<Int> = buildList {
        for (x in 0 until 128 step 2) addAll(listOf(x, 0))
        for (z in 0 until 128 step 2) addAll(listOf(128, z))
        for (x in 128 downTo 2 step 2) addAll(listOf(x, 128))
        for (z in 128 downTo 2 step 2) addAll(listOf(0, z))
    }.toMutableList()
}
