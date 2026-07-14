package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectOverlayServiceTest {
    private val scopeId = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(7, 0)))

    @AfterTest
    fun clearOverlays() {
        EffectOverlayService.clearScope(scopeId)
    }

    @Test
    fun `clearing a scope removes its transient overlays`() {
        val overlay = overlay()
        EffectOverlayService.applyTimedEffectOverlayForExistingScope(overlay)

        assertEquals(1, EffectOverlayService.queryOverlay(scopeId, 1)[EffectKey.SPEED])

        EffectOverlayService.clearScope(scopeId)

        assertTrue(EffectOverlayService.queryOverlay(scopeId, 1).isEmpty())
    }

    @Test
    fun `overlay rejects invalid identity timing and effects`() {
        assertFailsWith<IllegalArgumentException> { overlay(id = " ") }
        assertFailsWith<IllegalArgumentException> { overlay(source = " ") }
        assertFailsWith<IllegalArgumentException> { overlay(start = 1, end = 1) }
        assertFailsWith<IllegalArgumentException> { overlay(start = 2, end = 1) }
        assertFailsWith<IllegalArgumentException> { overlay(effects = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            overlay(effects = listOf(TimedEffect(EffectKey.SPEED, 1), TimedEffect(EffectKey.SPEED, 2)))
        }
        assertFailsWith<IllegalArgumentException> { overlay(effects = listOf(TimedEffect(EffectKey.SPEED, -1))) }
        assertFailsWith<IllegalArgumentException> { overlay(effects = listOf(TimedEffect(EffectKey.SPEED, 256))) }
    }

    @Test
    fun `service snapshots effects and replaces overlays atomically by id`() {
        val effects = mutableListOf(TimedEffect(EffectKey.SPEED, 1))
        EffectOverlayService.applyTimedEffectOverlayForExistingScope(overlay(effects = effects))
        effects.clear()

        assertEquals(1, EffectOverlayService.queryOverlay(scopeId, 1)[EffectKey.SPEED])
        val storedEffects = EffectOverlayService.queryActiveOverlays(scopeId, 1).single().effects
        assertFailsWith<UnsupportedOperationException> { (storedEffects as MutableList).clear() }

        EffectOverlayService.applyTimedEffectOverlayForExistingScope(
            overlay(effects = listOf(TimedEffect(EffectKey.HASTE, 2)))
        )

        val resolved = EffectOverlayService.queryOverlay(scopeId, 1)
        assertNull(resolved[EffectKey.SPEED])
        assertEquals(2, resolved[EffectKey.HASTE])
    }

    @Test
    fun `sweep removes overlays whose end is reached`() {
        EffectOverlayService.applyTimedEffectOverlayForExistingScope(overlay(end = 10))

        EffectOverlayService.sweepExpired(10)

        assertFalse(EffectOverlayService.clearTimedEffectOverlay(scopeId, "overlay"))
    }

    private fun overlay(
        id: String = "overlay",
        effects: List<TimedEffect> = listOf(TimedEffect(EffectKey.SPEED, 1)),
        start: Long = 0,
        end: Long = Long.MAX_VALUE,
        source: String = "test"
    ) = TimedEffectOverlay(id, scopeId.raw, effects, start, end, 0, source)
}
