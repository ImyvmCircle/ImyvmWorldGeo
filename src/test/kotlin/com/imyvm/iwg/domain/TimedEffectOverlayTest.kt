package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import kotlin.test.Test
import kotlin.test.assertEquals

class TimedEffectOverlayTest {
    @Test
    @Suppress("DEPRECATION")
    fun `epoch accessors alias legacy data class properties without changing copy shape`() {
        val overlay = TimedEffectOverlay(
            "overlay",
            generateCompatScopeIdRaw(1, 1),
            listOf(TimedEffect(EffectKey.SPEED, 1)),
            startTickMillis = 100,
            endTickMillis = 200,
            priority = 0,
            source = "test"
        )

        assertEquals(overlay.startTickMillis, overlay.startEpochMillis)
        assertEquals(overlay.endTickMillis, overlay.endEpochMillis)
        assertEquals(100, overlay.component4())
        assertEquals(200, overlay.component5())

        val copied = overlay.copy(startTickMillis = 125, endTickMillis = 225)
        assertEquals(125, copied.startEpochMillis)
        assertEquals(225, copied.endEpochMillis)
    }
}
