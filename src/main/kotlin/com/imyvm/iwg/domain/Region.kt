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
            val personalSettings = mutableListOf<String>()
            val globalSettings = mutableListOf<String>()

            settings.forEach { s ->
                val display = if (s.isPersonal) {
                    val playerName = resolvePlayerName(server, s.playerUUID)
                    "${s.key}=${s.value} (Player $playerName)"
                } else {
                    "${s.key}=${s.value}"
                }
                if (s.isPersonal) personalSettings.add(display)
                else globalSettings.add(display)
            }

            val result = mutableListOf<Text>()

            if (globalSettings.isNotEmpty()) {
                val combined = globalSettings.joinToString(", ")
                val text = if (scopeName == null) {
                    Translator.tr(key, combined)
                } else {
                    Translator.tr(key, scopeName, combined)
                }
                text?.let { result.add(it) }
            }

            if (personalSettings.isNotEmpty()) {
                val combined = personalSettings.joinToString(", ")
                val text = if (scopeName == null) {
                    Translator.tr("${key}.personal", combined)
                } else {
                    Translator.tr("${key}.personal", scopeName, combined)
                }
                text?.let { result.add(it) }
            }

            return result
        }
    }
}