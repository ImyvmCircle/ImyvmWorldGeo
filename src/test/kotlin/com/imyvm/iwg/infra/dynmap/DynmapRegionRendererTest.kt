package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DynmapRegionRendererTest {
    @Test
    fun `marker ids use stable assigned scope identity`() {
        val first = scope("a-b", 0)
        val collidingName = scope("a_b", 1)
        val renamed = scope("renamed", 0)

        assertEquals("iwg_${first.requireAssignedScopeId().toIdString()}", dynmapScopeMarkerId(first))
        assertEquals("iwgtp_${first.requireAssignedScopeId().toIdString()}", dynmapTeleportMarkerId(first))
        assertNotEquals(dynmapScopeMarkerId(first), dynmapScopeMarkerId(collidingName))
        assertEquals(dynmapScopeMarkerId(first), dynmapScopeMarkerId(renamed))
        assertNotEquals(dynmapScopeMarkerId(first), dynmapTeleportMarkerId(first))
    }

    private fun scope(name: String, index: Int) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(7, index))
    )
}
