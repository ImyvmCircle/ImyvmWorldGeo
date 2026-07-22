package com.imyvm.iwg.infra.config

import com.imyvm.iwg.domain.component.MAX_TELEPORT_FALLBACK_SEARCH_RADIUS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigValidationTest {
    @Test
    fun `integer bounds reject unsafe values`() {
        assertFailsWith<IllegalArgumentException> { positiveInt("positive", 0) }
        assertFailsWith<IllegalArgumentException> { nonNegativeInt("non-negative", -1) }
        assertEquals(1, positiveInt("positive", 1))
        assertEquals(0, nonNegativeInt("non-negative", 0))
    }

    @Test
    fun `relations reject short effects`() {
        assertFailsWith<IllegalArgumentException> { validateConfigRelations(1, 1) }
        validateConfigRelations(2, 1)
    }

    @Test
    fun `runtime updates roll back invalid values`() {
        initializeConfigValidation()
        val oldTicker = CoreConfig.LAZY_TICKER_SECONDS.value
        val oldBehaviorStatsLimit = CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.value
        val oldEffect = EffectConfig.EFFECT_DURATION_SECONDS.value
        val oldGeographyBatchSize = GeoConfig.GEOGRAPHIC_REFRESH_BATCH_SIZE.value
        val oldFallbackRadius = TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value

        assertFailsWith<IllegalArgumentException> { CoreConfig.LAZY_TICKER_SECONDS.setValue(0) }
        assertEquals(oldTicker, CoreConfig.LAZY_TICKER_SECONDS.value)

        assertFailsWith<IllegalArgumentException> { CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.setValue(0) }
        assertEquals(oldBehaviorStatsLimit, CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.value)

        assertFailsWith<IllegalArgumentException> {
            EffectConfig.EFFECT_DURATION_SECONDS.setValue(CoreConfig.LAZY_TICKER_SECONDS.value)
        }
        assertEquals(oldEffect, EffectConfig.EFFECT_DURATION_SECONDS.value)

        assertFailsWith<IllegalArgumentException> { GeoConfig.GEOGRAPHIC_REFRESH_BATCH_SIZE.setValue(0) }
        assertEquals(oldGeographyBatchSize, GeoConfig.GEOGRAPHIC_REFRESH_BATCH_SIZE.value)

        assertFailsWith<IllegalArgumentException> {
            TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.setValue(
                MAX_TELEPORT_FALLBACK_SEARCH_RADIUS + 1
            )
        }
        assertEquals(oldFallbackRadius, TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value)
    }
}
