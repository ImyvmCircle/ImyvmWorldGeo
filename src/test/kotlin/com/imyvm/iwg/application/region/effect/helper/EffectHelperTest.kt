package com.imyvm.iwg.application.region.effect.helper

import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EffectHelperTest {
    private val playerId = UUID.randomUUID()
    private val scope = GeoScope(
        "scope",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(1, 0))
    )
    private val region = Region("region", 1, mutableListOf(scope))

    @AfterTest
    fun clearOverlays() {
        EffectOverlayService.clearScope(scope.requireAssignedScopeId())
    }

    @Test
    fun `scope effects resolve personal overlay and global layers in contract order`() {
        region.settingStore.put(EffectSetting(EffectKey.SPEED, 10))
        scope.settingStore.put(EffectSetting(EffectKey.SPEED, 20))
        region.settingStore.put(EffectSetting(EffectKey.SPEED, 30, playerId))
        scope.settingStore.put(EffectSetting(EffectKey.SPEED, 40, playerId))
        applyOverlay(25)

        assertScopeValue(40)

        scope.settingStore.removeEffect(EffectKey.SPEED, SettingSubject.Player(playerId))
        assertScopeValue(30)

        region.settingStore.removeEffect(EffectKey.SPEED, SettingSubject.Player(playerId))
        assertScopeValue(25)

        EffectOverlayService.clearScope(scope.requireAssignedScopeId())
        assertScopeValue(20)

        scope.settingStore.removeEffect(EffectKey.SPEED, SettingSubject.Global)
        assertScopeValue(10)

        region.settingStore.removeEffect(EffectKey.SPEED, SettingSubject.Global)
        assertNull(getScopeEffectValue(region, scope, playerId, EffectKey.SPEED))
        assertEquals(emptyMap(), getScopeActiveEffects(region, scope, playerId))
    }

    @Test
    fun `region effects resolve personal before global`() {
        region.settingStore.put(EffectSetting(EffectKey.SPEED, 10))
        region.settingStore.put(EffectSetting(EffectKey.SPEED, 20, playerId))

        assertEquals(20, getRegionEffectValue(region, playerId, EffectKey.SPEED))
        assertEquals(20, getRegionActiveEffects(region, playerId)[EffectKey.SPEED])

        region.settingStore.removeEffect(EffectKey.SPEED, SettingSubject.Player(playerId))

        assertEquals(10, getRegionEffectValue(region, playerId, EffectKey.SPEED))
        assertEquals(10, getRegionActiveEffects(region, playerId)[EffectKey.SPEED])
    }

    private fun assertScopeValue(expected: Int) {
        assertEquals(expected, getScopeEffectValue(region, scope, playerId, EffectKey.SPEED))
        assertEquals(expected, getScopeActiveEffects(region, scope, playerId)[EffectKey.SPEED])
    }

    private fun applyOverlay(amplifier: Int) {
        val scopeId = scope.requireAssignedScopeId()
        val overlay = TimedEffectOverlay(
            "precedence",
            scopeId.raw,
            listOf(TimedEffect(EffectKey.SPEED, amplifier)),
            0,
            Long.MAX_VALUE,
            0,
            "test"
        )
        EffectOverlayService.applyTimedEffectOverlay(overlay) { it == scopeId }
    }
}
