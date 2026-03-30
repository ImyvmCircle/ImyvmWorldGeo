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

    fun trBase(base: String,subKeys: List<String>): List<Component> {
        return subKeys.mapNotNull { tr("$base.$it") }
    }

    fun tr(key: String?, vararg args: Any?): Component? {
        val raw = key?.let { languageInstance.get(it) }
        val formatted = if (args.isNotEmpty()) {
            raw?.let { java.text.MessageFormat.format(it, *args) }
        } else {
            raw
        }
        return formatted?.let { TextParser.parse(it) }
    }

    fun raw(key: String?, vararg args: Any?): String? {
        val rawStr = key?.let { languageInstance.get(it) }
        return if (args.isNotEmpty()) {
            rawStr?.let { java.text.MessageFormat.format(it, *args) }
        } else {
            rawStr
        }
    }

    private fun createLanguage(languageId: String) = HokiLanguage.create(
        HokiLanguage.getResourcePath(MOD_ID, languageId)
            .let { Translator::class.java.getResourceAsStream(it) }
    )
}