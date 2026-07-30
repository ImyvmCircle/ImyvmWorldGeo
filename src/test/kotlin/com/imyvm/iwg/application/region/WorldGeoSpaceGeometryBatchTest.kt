package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGeoSpaceGeometryBatchTest {
    @Test
    fun `adjacent overlapping spaces reuse unique dimension chunk keys`() {
        val dimensionId = Identifier.parse("minecraft:overworld")
        val first = GeoScope(
            "first",
            dimensionId,
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(31, 15)),
            scopeId = ScopeId(generateCompatScopeIdRaw(20, 1))
        )
        val second = GeoScope(
            "second",
            dimensionId,
            null,
            geoShape = GeoShape.rectangle(GeoPoint(16, 0), GeoPoint(47, 15)),
            scopeId = ScopeId(generateCompatScopeIdRaw(20, 2))
        )

        val chunks = WorldGeoSpaceGeometrySupport.coveredChunks(
            listOf(
                WorldGeoSpaceGeometrySupport.scope(first),
                WorldGeoSpaceGeometrySupport.scope(second)
            )
        )

        assertEquals(listOf(0, 1, 2), chunks.map { it.chunkX }.sorted())
        assertEquals(3, chunks.size)
    }
}
