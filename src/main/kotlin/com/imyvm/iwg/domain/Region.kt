package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.Setting
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.ExtensionRuleSetting
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component

class Region(
    var name: String,
    var numberID: Int,
    var geometryScope: MutableList<GeoScope>,
    var settings: MutableList<Setting> = mutableListOf(),
    var showOnDynmap: Boolean = true
) {
    fun getScopeByName(scopeName: String): GeoScope {
        return geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw IllegalArgumentException(Translator.tr("region.error.no_scope", scopeName, name)!!.string)
    }

    fun getScopeInfos(server: MinecraftServer): List<Component> {
        val infos = mutableListOf<Component>()
        geometryScope.forEachIndexed { index, geoScope ->
            geoScope.getScopeInfo(index)?.let { infos.add(it) }
            infos.addAll(geoScope.getSettingInfos(server))
        }
        return infos
    }

    fun getSettingInfos(server: MinecraftServer): List<Component> {
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
        ): List<Component> {
            if (settings.isEmpty()) return emptyList()

            val result = mutableListOf<Component>()

            val headerText = if (scopeName == null) Translator.tr("$key.header")!!
                             else Translator.tr("$key.header", scopeName)!!
            headerText?.let { result.add(it) }

            val globalSettings = settings.filter { !it.isPersonal }
            if (globalSettings.isNotEmpty()) {
                Translator.tr("$key.global.header")!!?.let { result.add(it) }
                appendTypeGroups(result, globalSettings, key)
            }

            settings.filter { it.isPersonal }
                .groupBy { it.playerUUID }
                .forEach { (uuid, playerSettings) ->
                    val playerName = resolvePlayerName(server, uuid)
                    Translator.tr("$key.personal.header", playerName)!!?.let { result.add(it) }
                    appendTypeGroups(result, playerSettings, key)
                }

            return result
        }

        private fun appendTypeGroups(result: MutableList<Component>, settings: List<Setting>, key: String) {
            val permissions = settings.filter { it is PermissionSetting || it is ExtensionPermissionSetting }
            if (permissions.isNotEmpty()) {
                Translator.tr("$key.permission.header")!!?.let { result.add(it) }
                permissions.forEach { s -> Translator.tr("$key.item", s.key, s.value)!!?.let { result.add(it) } }
            }

            val effects = settings.filterIsInstance<EffectSetting>()
            if (effects.isNotEmpty()) {
                Translator.tr("$key.effect.header")!!?.let { result.add(it) }
                effects.forEach { s -> Translator.tr("$key.item", s.key, s.value)!!?.let { result.add(it) } }
            }

            val rules = settings.filter { it is RuleSetting || it is ExtensionRuleSetting }
            if (rules.isNotEmpty()) {
                Translator.tr("$key.rule.header")!!?.let { result.add(it) }
                rules.forEach { s -> Translator.tr("$key.item", s.key, s.value)!!?.let { result.add(it) } }
            }

            val notifToggles = settings.filterIsInstance<EntryExitToggleSetting>()
            val notifMessages = settings.filterIsInstance<EntryExitMessageSetting>()
            if (notifToggles.isNotEmpty() || notifMessages.isNotEmpty()) {
                Translator.tr("$key.entry_exit.header")!!?.let { result.add(it) }
                notifToggles.forEach { s -> Translator.tr("$key.item", s.key, s.value)!!?.let { result.add(it) } }
                notifMessages.forEach { s -> Translator.tr("$key.item", s.key, "&r\"${s.value}\"")!!?.let { result.add(it) } }
            }
        }
    }
}
