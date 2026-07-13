package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectOverlayServiceTest {
    @Test
    fun `clearing a scope removes its transient overlays`() {
        val scopeId = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(7, 0)))
        val overlay = TimedEffectOverlay(
            "overlay",
            scopeId.raw,
            listOf(TimedEffect(EffectKey.SPEED, 1)),
            0,
            Long.MAX_VALUE,
            0,
            "test"
        )
        EffectOverlayService.applyTimedEffectOverlayForExistingScope(overlay)

        assertEquals(1, EffectOverlayService.queryOverlay(scopeId, 1)[EffectKey.SPEED])

        EffectOverlayService.clearScope(scopeId)

        assertTrue(EffectOverlayService.queryOverlay(scopeId, 1).isEmpty())
    }
}
