package com.imyvm.iwg.inter.api

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegionDataApiTest {
    @Test
    fun `region scopes are returned as a snapshot`() {
        val first = scope("first")
        val region = Region("region", 1, mutableListOf(first))

        val snapshot = RegionDataApi.getRegionScopes(region)
        region.addScope(scope("second", 2))

        assertEquals(listOf(first), snapshot)
    }

    @Test
    fun `timed overlay factory snapshots effects`() {
        val effects = mutableListOf(TimedEffect(EffectKey.SPEED, 1))
        val scopeId = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(1, 0)))

        val overlay = RegionDataApi.createTimedEffectOverlay(
            "overlay", scopeId, effects, 0, 1, 0, "test"
        )
        effects.clear()

        assertEquals(listOf(TimedEffect(EffectKey.SPEED, 1)), overlay.effects)
        assertFailsWith<UnsupportedOperationException> { (overlay.effects as MutableList).clear() }
    }

    private fun scope(name: String, id: Long = 1) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(1, id.toInt()))
    )
}
