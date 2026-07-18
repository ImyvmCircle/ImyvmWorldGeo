package com.imyvm.iwg.util.text

import com.imyvm.hoki.i18n.HokiLanguage
import com.imyvm.hoki.i18n.HokiTranslator
import com.imyvm.iwg.ImyvmWorldGeo.Companion.MOD_ID
import com.imyvm.iwg.infra.config.CoreConfig
import net.minecraft.network.chat.Component

object Translator : HokiTranslator() {
    private var languageInstance = createLanguage(CoreConfig.LANGUAGE.value)

    init {
        CoreConfig.LANGUAGE.changeEvents.register { option, _, _ ->
            languageInstance = createLanguage(option.value)
        }
    }

    fun trAll(keys: List<String>): List<Component> {
        return keys.map(::tr)
    }

    @Deprecated("Pass complete translation keys to trAll instead")
    fun trBase(base: String, subKeys: List<String>): List<Component> {
        return trAll(subKeys.map { "$base.$it" })
    }

    fun tr(key: String, vararg args: Any?): Component {
        return TextParser.parse(formatRequired(key, args))
    }

    fun raw(key: String, vararg args: Any?): String {
        return formatRequired(key, args)
    }

    fun hasTranslation(key: String): Boolean {
        return languageInstance.hasTranslation(key)
    }

    private fun formatRequired(key: String, args: Array<out Any?>): String {
        val language = languageInstance
        check(language.hasTranslation(key)) { "Missing required translation key: $key" }
        val template = language.get(key)
        return if (args.isNotEmpty()) {
            java.text.MessageFormat.format(template, *args)
        } else {
            template
        }
    }

    private fun createLanguage(languageId: String) = HokiLanguage.create(
        HokiLanguage.getResourcePath(MOD_ID, languageId)
            .let { Translator::class.java.getResourceAsStream(it) }
    )
}
