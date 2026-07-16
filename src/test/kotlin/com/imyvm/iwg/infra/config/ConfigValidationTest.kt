package com.imyvm.iwg.infra.config

import com.imyvm.iwg.domain.component.MAX_TELEPORT_FALLBACK_SEARCH_RADIUS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigValidationTest {
    @Test
    fun `effect duration converts only values representable by minecraft tick API`() {
        val maxSeconds = Int.MAX_VALUE / 20

        assertEquals(20, effectDurationTicks(1))
        assertEquals(2_147_483_640, effectDurationTicks(maxSeconds))
        assertFailsWith<IllegalArgumentException> { effectDurationTicks(0) }
        assertFailsWith<IllegalArgumentException> { effectDurationTicks(-1) }
        assertFailsWith<IllegalArgumentException> { effectDurationTicks(maxSeconds + 1) }
        assertFailsWith<IllegalArgumentException> { effectDurationTicks(Int.MAX_VALUE) }
    }

    @Test
    fun `integer bounds reject unsafe values`() {
        assertFailsWith<IllegalArgumentException> { positiveInt("positive", 0) }
        assertFailsWith<IllegalArgumentException> { nonNegativeInt("non-negative", -1) }
        assertEquals(1, positiveInt("positive", 1))
        assertEquals(0, nonNegativeInt("non-negative", 0))
    }

    @Test
    fun `relations reject inverted selection and short effects`() {
        assertFailsWith<IllegalArgumentException> { validateConfigRelations(3, 2, 5, 1) }
        assertFailsWith<IllegalArgumentException> { validateConfigRelations(1, 2, 1, 1) }
        validateConfigRelations(1, 2, 2, 1)
    }

    @Test
    fun `runtime updates roll back invalid values`() {
        initializeConfigValidation()
        val oldTicker = CoreConfig.LAZY_TICKER_SECONDS.value
        val oldEffect = EffectConfig.EFFECT_DURATION_SECONDS.value
        val oldFallbackRadius = TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value

        try {
            assertFailsWith<IllegalArgumentException> { CoreConfig.LAZY_TICKER_SECONDS.setValue(0) }
            assertEquals(oldTicker, CoreConfig.LAZY_TICKER_SECONDS.value)

            assertFailsWith<IllegalArgumentException> {
                EffectConfig.EFFECT_DURATION_SECONDS.setValue(CoreConfig.LAZY_TICKER_SECONDS.value)
            }
            assertEquals(oldEffect, EffectConfig.EFFECT_DURATION_SECONDS.value)

            assertFailsWith<IllegalArgumentException> {
                EffectConfig.EFFECT_DURATION_SECONDS.setValue(Int.MAX_VALUE / 20 + 1)
            }
            assertEquals(oldEffect, EffectConfig.EFFECT_DURATION_SECONDS.value)

            assertFailsWith<IllegalArgumentException> {
                TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.setValue(
                    MAX_TELEPORT_FALLBACK_SEARCH_RADIUS + 1
                )
            }
            assertEquals(oldFallbackRadius, TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value)
        } finally {
            CoreConfig.LAZY_TICKER_SECONDS.setValue(oldTicker)
            EffectConfig.EFFECT_DURATION_SECONDS.setValue(oldEffect)
            TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.setValue(oldFallbackRadius)
        }
    }

    @Test
    fun `double geometry bounds reject non-finite and negative values`() {
        assertFailsWith<IllegalArgumentException> { finiteNonNegativeDouble("test", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { finiteNonNegativeDouble("test", Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { finiteNonNegativeDouble("test", Double.NEGATIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { finiteNonNegativeDouble("test", -0.001) }
        assertEquals(0.0, finiteNonNegativeDouble("test", 0.0))
        assertEquals(5000.0, finiteNonNegativeDouble("test", 5000.0))
    }

    @Test
    fun `aspect ratio rejects out-of-range values`() {
        assertFailsWith<IllegalArgumentException> { finiteAspectRatio("test", 0.0) }
        assertFailsWith<IllegalArgumentException> { finiteAspectRatio("test", -0.1) }
        assertFailsWith<IllegalArgumentException> { finiteAspectRatio("test", 1.01) }
        assertFailsWith<IllegalArgumentException> { finiteAspectRatio("test", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { finiteAspectRatio("test", Double.POSITIVE_INFINITY) }
        assertEquals(Double.MIN_VALUE, finiteAspectRatio("test", Double.MIN_VALUE))
        assertEquals(1.0, finiteAspectRatio("test", 1.0))
        assertEquals(0.2, finiteAspectRatio("test", 0.2))
    }

    @Test
    fun `double geometry options roll back invalid runtime updates`() {
        initializeConfigValidation()
        val oldArea = GeoConfig.MIN_RECTANGLE_AREA.value
        val oldRatio = GeoConfig.MIN_ASPECT_RATIO.value

        try {
            assertFailsWith<IllegalArgumentException> { GeoConfig.MIN_RECTANGLE_AREA.setValue(Double.NaN) }
            assertEquals(oldArea, GeoConfig.MIN_RECTANGLE_AREA.value)

            assertFailsWith<IllegalArgumentException> { GeoConfig.MIN_RECTANGLE_AREA.setValue(-1.0) }
            assertEquals(oldArea, GeoConfig.MIN_RECTANGLE_AREA.value)

            assertFailsWith<IllegalArgumentException> { GeoConfig.MIN_ASPECT_RATIO.setValue(0.0) }
            assertEquals(oldRatio, GeoConfig.MIN_ASPECT_RATIO.value)

            assertFailsWith<IllegalArgumentException> { GeoConfig.MIN_ASPECT_RATIO.setValue(1.5) }
            assertEquals(oldRatio, GeoConfig.MIN_ASPECT_RATIO.value)

            GeoConfig.MIN_RECTANGLE_AREA.setValue(0.0)
            assertEquals(0.0, GeoConfig.MIN_RECTANGLE_AREA.value)

            GeoConfig.MIN_ASPECT_RATIO.setValue(1.0)
            assertEquals(1.0, GeoConfig.MIN_ASPECT_RATIO.value)
        } finally {
            GeoConfig.MIN_RECTANGLE_AREA.setValue(oldArea)
            GeoConfig.MIN_ASPECT_RATIO.setValue(oldRatio)
        }
    }
}
