package com.imyvm.iwg.application.region.effect.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectHelperTest {
    @Test
    fun `resolved active effects prefer subspace over scope and region`() {
        val player = UUID.randomUUID()
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(10, 10)),
            settings = mutableListOf(EffectSetting(EffectKey.SPEED, 1)),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        )
        val subSpace = SubSpace(
            1,
            "plot",
            scope.requireAssignedScopeId(),
            scope.worldId,
            GeoShape.rectangle(GeoPoint(1, 1), GeoPoint(2, 2)),
            settings = mutableListOf(EffectSetting(EffectKey.SPEED, 3))
        )
        val region = Region(
            "region",
            7,
            mutableListOf(scope),
            settings = mutableListOf(EffectSetting(EffectKey.SPEED, 2)),
            subSpaces = mutableListOf(subSpace)
        )

        assertEquals(3, getResolvedActiveEffects(region, scope, subSpace, player)[EffectKey.SPEED])
        assertEquals(1, getResolvedActiveEffects(region, scope, null, player)[EffectKey.SPEED])
    }
}
