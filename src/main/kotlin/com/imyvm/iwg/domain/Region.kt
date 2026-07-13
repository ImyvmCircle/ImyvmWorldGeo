package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.AssignedScopeId
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
import kotlin.math.round

class Region(
    var name: String,
    numberID: Int,
    geometryScope: MutableList<GeoScope>,
    settings: MutableList<Setting> = mutableListOf(),
    var showOnDynmap: Boolean = true,
    ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>> = mutableMapOf()
) {
    private val stableNumberId = numberID.also { require(it > 0) { "region id must be positive" } }
    private val mutableScopes = mutableListOf<GeoScope>()
    private val mutableOwnershipHistory = mutableMapOf<AssignedScopeId, MutableList<ScopeOwnershipEntry>>()
    internal val settingStore = com.imyvm.iwg.domain.component.SettingStore(settings)

    init {
        replaceScopes(geometryScope)
        replaceLegacyOwnershipHistory(ownershipHistoryByScope)
    }

    /** Binary-compatible facade. A Region identity cannot be replaced after construction. */
    var numberID: Int
        get() = stableNumberId
        set(value) = require(value == stableNumberId) { "region id cannot be changed" }

    internal val scopes: List<GeoScope>
        get() = mutableScopes

    /**
     * Binary-compatible snapshot for existing addons.
     *
     * Mutating the returned list does not change this Region. Use the addon API for writes.
     */
    var geometryScope: MutableList<GeoScope>
        get() = mutableScopes.toMutableList()
        set(value) = replaceScopes(value)

    /**
     * Binary-compatible snapshot for existing addons.
     *
     * The map and its entry lists are detached from this Region.
     */
    var ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>>
        get() = mutableOwnershipHistory.entries.associateTo(mutableMapOf()) { (scopeId, entries) ->
            scopeId.raw to entries.toMutableList()
        }
        set(value) = replaceLegacyOwnershipHistory(value)

    /**
     * Binary-compatible view for existing addons.
     *
     * The getter returns a detached snapshot. Mutating that list does not change this Region.
     * Use `RegionDataApi` for reads and `PlayerInteractionApi` setting operations for writes.
     * The JVM getter, setter, and constructor parameter are retained with no scheduled removal.
     */
    var settings: MutableList<Setting>
        get() = settingStore.toLegacyList().toMutableList()
        set(value) = settingStore.replaceAll(value)

    fun getScopeByName(scopeName: String): GeoScope {
        return mutableScopes.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw IllegalArgumentException(Translator.tr("region.error.no_scope", scopeName, name)!!.string)
    }

    fun containsScope(scope: GeoScope): Boolean = mutableScopes.any { it === scope }

    fun addScope(scope: GeoScope) {
        validateScope(scope, mutableScopes)
        mutableScopes.add(scope)
    }

    fun removeScope(scope: GeoScope): Int {
        val index = mutableScopes.indexOfFirst { it === scope }
        require(index >= 0) { "scope does not belong to region" }
        mutableScopes.removeAt(index)
        return index
    }

    fun restoreScope(index: Int, scope: GeoScope) {
        validateScope(scope, mutableScopes)
        mutableScopes.add(index.coerceIn(0, mutableScopes.size), scope)
    }

    internal fun restoreScopes(scopes: List<GeoScope>) = replaceScopes(scopes)

    fun renameScope(scope: GeoScope, newName: String) {
        require(containsScope(scope)) { "scope does not belong to region" }
        require(mutableScopes.none { it !== scope && it.scopeName.equals(newName, ignoreCase = true) }) {
            "duplicate scope name"
        }
        scope.scopeName = newName
    }

    fun recordScopeOwnership(entry: ScopeOwnershipEntry) {
        require(entry.toRegionNumberId == numberID) { "ownership entry targets another region" }
        mutableOwnershipHistory.getOrPut(entry.scopeId) { mutableListOf() }.add(entry)
    }

    internal fun ownershipHistory(scopeId: AssignedScopeId): List<ScopeOwnershipEntry> =
        mutableOwnershipHistory[scopeId].orEmpty()

    internal fun ownershipHistorySnapshot(): MutableMap<AssignedScopeId, MutableList<ScopeOwnershipEntry>> =
        mutableOwnershipHistory.mapValuesTo(mutableMapOf()) { (_, entries) -> entries.toMutableList() }

    internal fun replaceOwnershipHistory(value: Map<AssignedScopeId, List<ScopeOwnershipEntry>>) {
        require(value.all { (scopeId, entries) -> entries.all { it.scopeId == scopeId } }) {
            "ownership history key does not match entry"
        }
        mutableOwnershipHistory.clear()
        value.forEach { (scopeId, entries) -> mutableOwnershipHistory[scopeId] = entries.toMutableList() }
    }

    private fun replaceLegacyOwnershipHistory(value: Map<Long, List<ScopeOwnershipEntry>>) {
        val typed = value.entries.associate { (raw, entries) ->
            val scopeId = AssignedScopeId.fromRaw(raw)
                ?: throw IllegalArgumentException("ownership history scope id is not assigned")
            require(entries.all { it.scopeId == scopeId }) { "ownership history key does not match entry" }
            scopeId to entries
        }
        replaceOwnershipHistory(typed)
    }

    private fun replaceScopes(scopes: List<GeoScope>) {
        val names = hashSetOf<String>()
        val assignedIds = hashSetOf<com.imyvm.iwg.domain.component.AssignedScopeId>()
        scopes.forEach { scope ->
            require(names.add(scope.scopeName.lowercase())) { "duplicate scope name" }
            require(assignedIds.add(scope.requireAssignedScopeId())) { "duplicate scope id" }
        }
        mutableScopes.clear()
        mutableScopes.addAll(scopes)
    }

    private fun validateScope(scope: GeoScope, existing: List<GeoScope>) {
        require(existing.none { it.scopeName.equals(scope.scopeName, ignoreCase = true) }) { "duplicate scope name" }
        val scopeId = scope.requireAssignedScopeId()
        require(existing.none { it.requireAssignedScopeId() == scopeId }) { "duplicate scope id" }
    }

    fun getScopeInfos(server: MinecraftServer): List<Component> {
        val infos = mutableListOf<Component>()
        mutableScopes.forEachIndexed { index, geoScope ->
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
        for (scope in mutableScopes) {
            scope.geoShape?.let {
                totalArea += it.calculateArea()
            }
        }
        return round(totalArea * 100.0) / 100.0
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

            val headerText = if (scopeName == null) Translator.tr("$key.header")
                             else Translator.tr("$key.header", scopeName)
            headerText?.let { result.add(it) }

            val globalSettings = settings.filter { !it.isPersonal }
            if (globalSettings.isNotEmpty()) {
                Translator.tr("$key.global.header")?.let { result.add(it) }
                appendTypeGroups(result, globalSettings, key)
            }

            settings.filter { it.isPersonal }
                .groupBy { it.playerUUID }
                .forEach { (uuid, playerSettings) ->
                    val playerName = resolvePlayerName(server, uuid)
                    Translator.tr("$key.personal.header", playerName)?.let { result.add(it) }
                    appendTypeGroups(result, playerSettings, key)
                }

            return result
        }

        private fun appendTypeGroups(result: MutableList<Component>, settings: List<Setting>, key: String) {
            val permissions = settings.filter { it is PermissionSetting || it is ExtensionPermissionSetting }
            if (permissions.isNotEmpty()) {
                Translator.tr("$key.permission.header")?.let { result.add(it) }
                permissions.forEach { s -> Translator.tr("$key.item", s.key, s.value)?.let { result.add(it) } }
            }

            val effects = settings.filterIsInstance<EffectSetting>()
            if (effects.isNotEmpty()) {
                Translator.tr("$key.effect.header")?.let { result.add(it) }
                effects.forEach { s -> Translator.tr("$key.item", s.key, s.value)?.let { result.add(it) } }
            }

            val rules = settings.filter { it is RuleSetting || it is ExtensionRuleSetting }
            if (rules.isNotEmpty()) {
                Translator.tr("$key.rule.header")?.let { result.add(it) }
                rules.forEach { s -> Translator.tr("$key.item", s.key, s.value)?.let { result.add(it) } }
            }

            val notifToggles = settings.filterIsInstance<EntryExitToggleSetting>()
            val notifMessages = settings.filterIsInstance<EntryExitMessageSetting>()
            if (notifToggles.isNotEmpty() || notifMessages.isNotEmpty()) {
                Translator.tr("$key.entry_exit.header")?.let { result.add(it) }
                notifToggles.forEach { s -> Translator.tr("$key.item", s.key, s.value)?.let { result.add(it) } }
                notifMessages.forEach { s -> Translator.tr("$key.item", s.key, "&r\"${s.value}\"")?.let { result.add(it) } }
            }
        }
    }
}
