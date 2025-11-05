package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.Setting
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text

class Region(
    var name: String,
    var numberID: Int,
    var geometryScope: MutableList<GeoScope>,
    var settings: MutableList<Setting> = mutableListOf()
) {
    fun getScopeByName(scopeName: String): GeoScope {
        return geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw IllegalArgumentException(Translator.tr("region.error.no_scope", scopeName, name)!!.string)
    }

    fun getScopeInfos(server: MinecraftServer): List<Text> {
        val infos = mutableListOf<Text>()
        geometryScope.forEachIndexed { index, geoScope ->
            geoScope.getScopeInfo(index)?.let { infos.add(it) }
            infos.addAll(geoScope.getSettingInfos(server))
        }
        return infos
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return formatSettings(server, settings, "region.setting")
    }

    fun calculateTotalArea(): Double {
        var totalArea = 0.0
        for (scope in geometryScope) {
            scope.geoShape?.let {
                totalArea += it.calculateArea()
            }
        }
        return "%.2f".format(totalArea).toDouble()
    }

    companion object {
        fun formatSettings(
            server: MinecraftServer,
            settings: List<Setting>,
            key: String,
            scopeName: String? = null
        ): List<Text> {
            val (personalSettings, globalSettings) = groupAndFormatSettings(server, settings)

            val result = mutableListOf<Text>()

            translateAndAppend(result, globalSettings, key, scopeName, isPersonal = false)
            translateAndAppend(result, personalSettings, key, scopeName, isPersonal = true)

            return result
        }

        private fun formatSettingDisplay(server: MinecraftServer, setting: Setting): String {
            val baseDisplay = "${setting.key}=${setting.value}"
            return if (setting.isPersonal) {
                val playerName = resolvePlayerName(server, setting.playerUUID)
                "$baseDisplay (Player $playerName)"
            } else {
                baseDisplay
            }
        }

        private fun groupAndFormatSettings(server: MinecraftServer, settings: List<Setting>): Pair<List<String>, List<String>> {
            val personalSettings = mutableListOf<String>()
            val globalSettings = mutableListOf<String>()

            settings.forEach { s ->
                val display = formatSettingDisplay(server, s)
                if (s.isPersonal) {
                    personalSettings.add(display)
                } else {
                    globalSettings.add(display)
                }
            }

            return Pair(personalSettings, globalSettings)
        }

        private fun translateAndAppend(
            result: MutableList<Text>,
            settings: List<String>,
            key: String,
            scopeName: String? = null,
            isPersonal: Boolean = false
        ) {
            if (settings.isNotEmpty()) {
                val combined = settings.joinToString(", ")
                val translationKey = if (isPersonal) "$key.personal" else key

                val text = if (scopeName == null) {
                    Translator.tr(translationKey, combined)
                } else {
                    Translator.tr(translationKey, scopeName, combined)
                }
                text?.let { result.add(it) }
            }
        }
    }
}