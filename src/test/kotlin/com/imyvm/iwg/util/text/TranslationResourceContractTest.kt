package com.imyvm.iwg.util.text

import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.text.MessageFormat
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationResourceContractTest {
    private val placeholder = Regex("""\{(\d+)}""")

    @Test
    fun `locales contain the same keys and placeholder indices`() {
        val english = loadLocale("en_us")
        val chinese = loadLocale("zh_cn")

        assertEquals(english.keys, chinese.keys)
        for (key in english.keys) {
            assertEquals(
                placeholderIndices(english.getValue(key)),
                placeholderIndices(chinese.getValue(key)),
                "Placeholder indices differ for $key"
            )
        }
    }

    @Test
    fun `every declared placeholder is substituted by MessageFormat`() {
        for (locale in listOf("en_us", "zh_cn")) {
            for ((key, template) in loadLocale(locale)) {
                val indices = placeholderIndices(template)
                if (indices.isEmpty()) continue
                val markers = Array(indices.max() + 1) { index -> "__ARG_${index}__" }
                val rendered = MessageFormat.format(template, *markers)

                for (index in indices) {
                    assertContains(
                        rendered,
                        markers[index],
                        message = "$locale:$key did not substitute placeholder {$index}"
                    )
                }
            }
        }
    }

    @Test
    fun `english apostrophes render literally without swallowing placeholders`() {
        val templates = loadLocale("en_us")
            .filterValues { "''" in it && placeholderIndices(it).isNotEmpty() }

        assertEquals(9, templates.size)
        for ((key, template) in templates) {
            val indices = placeholderIndices(template)
            val markers = Array(indices.max() + 1) { index -> "__ARG_${index}__" }
            val rendered = MessageFormat.format(template, *markers)

            assertTrue("'" in rendered, "$key did not render a literal apostrophe")
            assertTrue("''" !in rendered, "$key retained the MessageFormat escape")
            for (index in indices) assertContains(rendered, markers[index])
        }
    }

    private fun loadLocale(locale: String): Map<String, String> {
        val path = "/assets/imyvmworldgeo/lang/$locale.json"
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (key, value) ->
                key to value.asString
            }
        }
    }

    private fun placeholderIndices(template: String): Set<Int> =
        placeholder.findAll(template).map { it.groupValues[1].toInt() }.toSet()
}
