package com.imyvm.iwg.infra.config

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

        assertFailsWith<IllegalArgumentException> { CoreConfig.LAZY_TICKER_SECONDS.setValue(0) }
        assertEquals(oldTicker, CoreConfig.LAZY_TICKER_SECONDS.value)

        assertFailsWith<IllegalArgumentException> {
            EffectConfig.EFFECT_DURATION_SECONDS.setValue(CoreConfig.LAZY_TICKER_SECONDS.value)
        }
        assertEquals(oldEffect, EffectConfig.EFFECT_DURATION_SECONDS.value)
    }
}
