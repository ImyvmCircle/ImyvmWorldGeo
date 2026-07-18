package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.util.text.Translator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InformationApplicationTest {
    @Test
    fun `help order contains complete unique required keys`() {
        assertEquals(30, HELP_TRANSLATION_KEYS.size)
        assertEquals(HELP_TRANSLATION_KEYS.size, HELP_TRANSLATION_KEYS.distinct().size)
        assertEquals("interaction.meta.command.help.header", HELP_TRANSLATION_KEYS.first())
        assertTrue(HELP_TRANSLATION_KEYS.all(Translator::hasTranslation))
        assertEquals(HELP_TRANSLATION_KEYS.size, Translator.trAll(HELP_TRANSLATION_KEYS).size)
    }
}
