package com.imyvm.iwg.util.text

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.network.chat.Component

class TranslatorTest {
    @Test
    fun `required translations format arguments and parse legacy colors`() {
        val translated = Translator.tr("interaction.meta.create.success", "demo")

        assertContains(translated.string, "demo")
        assertFalse(translated.string.contains('&'))
        assertTrue(hasColoredContent(translated))
        assertContains(Translator.raw("interaction.meta.create.success", "demo"), "demo")
    }

    @Test
    fun `required translations preserve newlines`() {
        val translated = Translator.tr("teleport_point.safety.feet_blocked", 1, 2, 3)

        assertContains(translated.string, "\n")
        assertContains(translated.string, "1")
        assertContains(translated.string, "2")
        assertContains(translated.string, "3")
    }

    @Test
    fun `missing required translation fails with the key`() {
        val key = "missing.required.translation"

        val failure = assertFailsWith<IllegalStateException> { Translator.tr(key) }

        assertContains(failure.message.orEmpty(), key)
    }

    @Test
    fun `translation presence can be checked without rendering a key fallback`() {
        assertTrue(Translator.hasTranslation("interaction.meta.create.success"))
        assertFalse(Translator.hasTranslation("missing.optional.translation"))
        assertEquals(false, Translator.hasTranslation(""))
    }

    private fun hasColoredContent(component: Component): Boolean =
        component.style.color != null || component.siblings.any(::hasColoredContent)
}
