package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoSpaceGeometryStatus
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WorldGeoSpaceGeometrySupportTest {
    private val overworld = Identifier.parse("minecraft:overworld")
    private val nether = Identifier.parse("minecraft:the_nether")

    @Test
    fun `scope exposes stable centroid full and partial chunk coverage`() {
        val full = scope(
            7,
            1,
            overworld,
            GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(15, 15))
        )
        val first = WorldGeoSpaceGeometrySupport.scope(full)
        val second = WorldGeoSpaceGeometrySupport.scope(full)
        val partial = WorldGeoSpaceGeometrySupport.scope(
            scope(
                7,
                2,
                overworld,
                GeoShape.rectangle(GeoPoint(16, 0), GeoPoint(23, 7))
            )
        )

        assertEquals(WorldGeoSpaceGeometryStatus.AVAILABLE, first.status)
        assertEquals(0, first.centroidChunk?.chunkX)
        assertEquals(0, first.centroidChunk?.chunkZ)
        assertEquals(1.0, first.chunkCoverage.single().areaRatio)
        assertEquals(first.geometryVersion, second.geometryVersion)
        assertEquals(1, partial.centroidChunk?.chunkX)
        assertEquals(0.25, partial.chunkCoverage.single().areaRatio)
        assertNotEquals(first.geometryVersion, partial.geometryVersion)
    }

    @Test
    fun `region unions adjacent scopes and separates dimensions`() {
        val first = scope(
            8,
            1,
            overworld,
            GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(15, 15))
        )
        val adjacent = scope(
            8,
            2,
            overworld,
            GeoShape.rectangle(GeoPoint(16, 0), GeoPoint(31, 15))
        )
        val otherDimension = scope(
            8,
            3,
            nether,
            GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(15, 15))
        )

        val facts = WorldGeoSpaceGeometrySupport.region(
            Region("region", 8, mutableListOf(first, adjacent, otherDimension))
        )

        assertEquals(listOf(overworld, nether), facts.map { it.dimensionId })
        assertEquals(2, facts[0].chunkCoverage.size)
        assertEquals(1, facts[1].chunkCoverage.size)
        assertEquals(0, facts[0].centroidChunk?.chunkX)
    }

    @Test
    fun `region without shapes reports no coverage`() {
        val empty = GeoScope(
            "empty",
            overworld,
            null,
            geoShape = null,
            scopeId = ScopeId(generateCompatScopeIdRaw(9, 1))
        )

        val fact = WorldGeoSpaceGeometrySupport.region(
            Region("empty_region", 9, mutableListOf(empty))
        ).single()

        assertEquals(WorldGeoSpaceGeometryStatus.NO_COVERAGE, fact.status)
        assertEquals(overworld, fact.dimensionId)
        assertNull(fact.centroidChunk)
        assertEquals(emptyList(), fact.chunkCoverage)
    }

    @Test
    fun `large region stops at chunk limit without reading chunks`() {
        val largeScope = scope(
            10,
            1,
            overworld,
            GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(1040, 1040))
        )
        val fact = WorldGeoSpaceGeometrySupport.region(
            Region("large_region", 10, mutableListOf(largeScope))
        ).single()

        assertEquals(WorldGeoSpaceGeometryStatus.CHUNK_LIMIT_EXCEEDED, fact.status)
        assertNull(fact.centroidChunk)
        assertEquals(emptyList(), fact.chunkCoverage)
    }

    @Test
    fun `subspace exposes its own geometry fact`() {
        val parent = scope(
            11,
            1,
            overworld,
            GeoShape.rectangle(GeoPoint(-32, -32), GeoPoint(31, 31))
        )
        val subSpace = SubSpace(
            55L,
            "plot",
            parent.requireAssignedScopeId(),
            overworld,
            GeoShape.circle(GeoPoint(-8, -8), 3)
        )

        val fact = WorldGeoSpaceGeometrySupport.subSpace(subSpace)

        assertEquals(55L, fact.spaceId)
        assertEquals(-1, fact.centroidChunk?.chunkX)
        assertEquals(-1, fact.centroidChunk?.chunkZ)
        assertEquals(WorldGeoSpaceGeometryStatus.AVAILABLE, fact.status)
    }

    private fun scope(
        regionId: Int,
        sequence: Int,
        dimensionId: Identifier,
        shape: GeoShape
    ): GeoScope = GeoScope(
        "scope_$sequence",
        dimensionId,
        null,
        geoShape = shape,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, sequence))
    )
}
