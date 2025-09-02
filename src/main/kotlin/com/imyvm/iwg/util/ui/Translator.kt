package com.imyvm.iwg.util.ui

import com.imyvm.hoki.i18n.HokiLanguage
import com.imyvm.hoki.i18n.HokiTranslator
import com.imyvm.iwg.ImyvmWorldGeo.Companion.MOD_ID
import com.imyvm.iwg.ModConfig
import net.minecraft.text.Text

object Translator : HokiTranslator() {
    private var languageInstance = createLanguage(ModConfig.LANGUAGE.value)

    init {
        ModConfig.LANGUAGE.changeEvents.register { option, _, _ ->
            languageInstance = createLanguage(option.value)
        }
    }

    fun tr(key: String?, vararg args: Any?): Text {
        return translate(languageInstance, key, *args)
    }

    private fun createLanguage(languageId: String) = HokiLanguage.create(
        HokiLanguage.getResourcePath(MOD_ID, languageId)
            .let { Translator::class.java.getResourceAsStream(it) }
    )
}