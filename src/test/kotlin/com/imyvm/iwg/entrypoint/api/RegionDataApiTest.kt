package com.imyvm.iwg.inter.api

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionDataApiTest {
    @Test
    fun `region scopes are returned as a snapshot`() {
        val first = scope("first")
        val region = Region("region", 1, mutableListOf(first))

        val snapshot = RegionDataApi.getRegionScopes(region)
        region.addScope(scope("second", 2))

        assertEquals(listOf(first), snapshot)
    }

    private fun scope(name: String, id: Long = 1) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(id)
    )
}
