package com.imyvm.iwg

import com.imyvm.hoki.i18n.HokiLanguage
import com.imyvm.hoki.i18n.HokiTranslator

object Translator : HokiTranslator() {
    private var languageInstance = createLanguage(ModConfig.LANGUAGE.value)

    init {
        ModConfig.LANGUAGE.changeEvents.register { option, _, _ ->
            languageInstance = createLanguage(option.value)
        }
    }

    fun tr(key: String?, vararg args: Any?) = translate(languageInstance, key, *args)

    private fun createLanguage(languageId: String) = HokiLanguage.create(
        HokiLanguage.getResourcePath("imyvm_world_geo", languageId)
            .let { Translator::class.java.getResourceAsStream(it) }
    )
}