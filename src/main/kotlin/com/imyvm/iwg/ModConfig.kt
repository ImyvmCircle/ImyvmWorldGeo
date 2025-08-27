package com.imyvm.iwg

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

class ModConfig : HokiConfig("Imyvm_world_geo.conf") {
    companion object{
        @JvmField
        @ConfigOption
        val LANGUAGE = Option(
            "core.language",
            "en_us",
            "the display language of Imyvm World Geo."
        ) { obj: Config, path: String? ->
            obj.getString(
                path
            )
        }
    }
}