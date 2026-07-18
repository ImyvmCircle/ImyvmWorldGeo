package com.imyvm.iwg.domain

import com.imyvm.iwg.util.text.Translator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalStatsCategoryTest {
    @Test
    fun `every category owns a complete required translation key`() {
        assertTrue(NaturalStatsCategory.entries.all { category ->
            category.translationKey.startsWith("interaction.meta.stats.category.") &&
                Translator.hasTranslation(category.translationKey)
        })
        assertEquals(
            NaturalStatsCategory.entries.size,
            NaturalStatsCategory.entries.map { it.translationKey }.distinct().size
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy translation suffix getter remains compatible`() {
        NaturalStatsCategory.entries.forEach { category ->
            assertEquals(category.commandName, category.translationSuffix)
        }
        NaturalStatsCategory::class.java.getMethod("getTranslationSuffix")
    }
}
