package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.WorldGeoBiomeCategory
import com.imyvm.iwg.domain.WorldGeoGeographicAttributeKind
import com.imyvm.iwg.domain.WorldGeoGeographicOrientation
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import net.minecraft.resources.Identifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorldGeoGeographicProfileSupportTest {
    @AfterTest
    fun tearDown() {
        WorldGeoGeographicProfileSupport.clearForTest()
    }

    @Test
    fun `overworld biome ids map to design categories`() {
        assertEquals(WorldGeoBiomeCategory.OCEAN, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:river")))
        assertEquals(WorldGeoBiomeCategory.OCEAN, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:beach")))
        assertEquals(WorldGeoBiomeCategory.RAINFOREST, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:mangrove_swamp")))
        assertEquals(WorldGeoBiomeCategory.RAINFOREST, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:jungle")))
        assertEquals(WorldGeoBiomeCategory.SNOWY, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:frozen_peaks")))
        assertEquals(WorldGeoBiomeCategory.DESERT, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:badlands")))
        assertEquals(WorldGeoBiomeCategory.FOREST, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:dark_forest")))
        assertEquals(WorldGeoBiomeCategory.MOUNTAIN, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:windswept_hills")))
        assertEquals(WorldGeoBiomeCategory.PLAINS, WorldGeoGeographicProfileSupport.classifyBiome(Identifier.parse("minecraft:savanna")))
    }

    @Test
    fun `ocean wins directly when it is the largest category`() {
        val (kind, categories) = WorldGeoGeographicProfileSupport.resolveAttribute(
            mapOf(
                WorldGeoBiomeCategory.OCEAN to 45,
                WorldGeoBiomeCategory.FOREST to 35,
                WorldGeoBiomeCategory.PLAINS to 20
            )
        )

        assertEquals(WorldGeoGeographicAttributeKind.BIOME_CLASS, kind)
        assertEquals(listOf(WorldGeoBiomeCategory.OCEAN), categories)
    }

    @Test
    fun `land category wins when it reaches half of total area after ocean is not dominant`() {
        val (kind, categories) = WorldGeoGeographicProfileSupport.resolveAttribute(
            mapOf(
                WorldGeoBiomeCategory.FOREST to 50,
                WorldGeoBiomeCategory.OCEAN to 30,
                WorldGeoBiomeCategory.PLAINS to 20
            )
        )

        assertEquals(WorldGeoGeographicAttributeKind.BIOME_CLASS, kind)
        assertEquals(listOf(WorldGeoBiomeCategory.FOREST), categories)
    }

    @Test
    fun `two land categories combine when they reach seventy percent`() {
        val (kind, categories) = WorldGeoGeographicProfileSupport.resolveAttribute(
            mapOf(
                WorldGeoBiomeCategory.FOREST to 40,
                WorldGeoBiomeCategory.PLAINS to 30,
                WorldGeoBiomeCategory.OCEAN to 20,
                WorldGeoBiomeCategory.DESERT to 10
            )
        )

        assertEquals(WorldGeoGeographicAttributeKind.COMBINED_BIOME_CLASS, kind)
        assertEquals(listOf(WorldGeoBiomeCategory.FOREST, WorldGeoBiomeCategory.PLAINS), categories)
    }

    @Test
    fun `mixed land remains diverse below combined threshold`() {
        val (kind, categories) = WorldGeoGeographicProfileSupport.resolveAttribute(
            mapOf(
                WorldGeoBiomeCategory.FOREST to 30,
                WorldGeoBiomeCategory.PLAINS to 25,
                WorldGeoBiomeCategory.DESERT to 25,
                WorldGeoBiomeCategory.OCEAN to 20
            )
        )

        assertEquals(WorldGeoGeographicAttributeKind.DIVERSE, kind)
        assertEquals(emptyList(), categories)
    }

    @Test
    fun `cache status records invalidation reason`() {
        WorldGeoGeographicProfileSupport.invalidateAll("test_reset")
        val status = WorldGeoGeographicProfileSupport.cacheStatus()

        assertEquals(0, status.cachedProfileCount)
        assertEquals("test_reset", status.lastInvalidationReason)
        assertNotNull(status.lastInvalidatedAtMillis)
    }

    @Test
    fun `scheduled refresh drains in batches and invalidate clears pending jobs`() {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:the_nether"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(10, 10)),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        )
        val subSpace = SubSpace(
            1,
            "plot",
            scope.requireAssignedScopeId(),
            scope.worldId,
            GeoShape.rectangle(GeoPoint(1, 1), GeoPoint(2, 2))
        )
        val region = Region("region", 7, mutableListOf(scope), subSpaces = mutableListOf(subSpace))

        assertEquals(3, WorldGeoGeographicProfileSupport.scheduleRefreshAll(listOf(region)))
        assertEquals(3, WorldGeoGeographicProfileSupport.pendingRefreshCount())
        assertEquals(
            1,
            WorldGeoGeographicProfileSupport.processScheduledRefresh(maxProfiles = 1) { }
        )
        assertEquals(2, WorldGeoGeographicProfileSupport.pendingRefreshCount())

        WorldGeoGeographicProfileSupport.invalidateAll("manual_reset")

        assertEquals(0, WorldGeoGeographicProfileSupport.pendingRefreshCount())
        assertEquals("manual_reset", WorldGeoGeographicProfileSupport.cacheStatus().lastInvalidationReason)
    }

    @Test
    fun `orientation uses axis threshold around origin`() {
        assertEquals(
            WorldGeoGeographicOrientation.CENTER,
            WorldGeoGeographicProfileSupport.orientation(GeoShape.rectangle(GeoPoint(-100, -100), GeoPoint(100, 100)))
        )
        assertEquals(
            WorldGeoGeographicOrientation.NORTH_AXIS,
            WorldGeoGeographicProfileSupport.orientation(GeoShape.rectangle(GeoPoint(-100, -6100), GeoPoint(100, -5900)))
        )
        assertEquals(
            WorldGeoGeographicOrientation.SOUTHEAST,
            WorldGeoGeographicProfileSupport.orientation(GeoShape.rectangle(GeoPoint(5000, 5000), GeoPoint(5400, 5400)))
        )
    }
}
