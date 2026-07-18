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
import com.imyvm.iwg.domain.component.isValidGeoName
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import java.util.Collections
import kotlin.math.round

class ScopeNotFoundException(
    val scopeName: String,
    val regionName: String
) : IllegalArgumentException("region.error.no_scope")

internal data class ScopeRemovalReceipt(
    val scope: GeoScope,
    val index: Int
)

internal data class SettingPresentationKeys(
    val header: String,
    val globalHeader: String,
    val personalHeader: String,
    val permissionHeader: String,
    val effectHeader: String,
    val ruleHeader: String,
    val entryExitHeader: String,
    val item: String
)

private val REGION_SETTING_PRESENTATION_KEYS = SettingPresentationKeys(
    header = "region.setting.header",
    globalHeader = "region.setting.global.header",
    personalHeader = "region.setting.personal.header",
    permissionHeader = "region.setting.permission.header",
    effectHeader = "region.setting.effect.header",
    ruleHeader = "region.setting.rule.header",
    entryExitHeader = "region.setting.entry_exit.header",
    item = "region.setting.item"
)

private val SCOPE_SETTING_PRESENTATION_KEYS = SettingPresentationKeys(
    header = "geo.scope.setting.header",
    globalHeader = "geo.scope.setting.global.header",
    personalHeader = "geo.scope.setting.personal.header",
    permissionHeader = "geo.scope.setting.permission.header",
    effectHeader = "geo.scope.setting.effect.header",
    ruleHeader = "geo.scope.setting.rule.header",
    entryExitHeader = "geo.scope.setting.entry_exit.header",
    item = "geo.scope.setting.item"
)

internal sealed interface SettingPresentationTarget {
    val keys: SettingPresentationKeys
    fun translateHeader(): Component

    data object RegionSettings : SettingPresentationTarget {
        override val keys = REGION_SETTING_PRESENTATION_KEYS

        override fun translateHeader(): Component = Translator.tr(keys.header)
    }

    data class ScopeSettings(val scopeName: String) : SettingPresentationTarget {
        override val keys = SCOPE_SETTING_PRESENTATION_KEYS

        override fun translateHeader(): Component = Translator.tr(keys.header, scopeName)
    }
}

internal fun legacySettingPresentationTarget(key: String, scopeName: String?): SettingPresentationTarget =
    when (key) {
        "region.setting" -> {
            require(scopeName == null) { "region setting presentation does not accept a scope name" }
            SettingPresentationTarget.RegionSettings
        }
        "geo.scope.setting" -> SettingPresentationTarget.ScopeSettings(
            requireNotNull(scopeName) { "scope setting presentation requires a scope name" }
        )
        else -> throw IllegalArgumentException("unsupported setting presentation key: $key")
    }

class Region(
    name: String,
    numberID: Int,
    geometryScope: MutableList<GeoScope>,
    settings: MutableList<Setting> = mutableListOf(),
    showOnDynmap: Boolean = true,
    ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>> = mutableMapOf()
) {
    private var currentName: String = name
    private var currentShowOnDynmap: Boolean = showOnDynmap

    /** Binary-compatible facade. Rename through the application boundary. */
    @set:Deprecated("Rename Regions through PlayerInteractionApi")
    var name: String
        get() = currentName
        set(value) {
            require(value == currentName) { "region name must be changed through the application boundary" }
        }

    /** Binary-compatible facade. Change visibility through the application boundary. */
    @set:Deprecated("Change Region Dynmap visibility through PlayerInteractionApi")
    var showOnDynmap: Boolean
        get() = currentShowOnDynmap
        set(value) {
            require(value == currentShowOnDynmap) {
                "Dynmap visibility must be changed through the application boundary"
            }
        }

    private val stableNumberId = numberID.also { require(it > 0) { "region id must be positive" } }
    private val mutableScopes = mutableListOf<GeoScope>()
    private val scopeView: List<GeoScope> = Collections.unmodifiableList(mutableScopes)
    private val mutableOwnershipHistory = mutableMapOf<AssignedScopeId, MutableList<ScopeOwnershipEntry>>()
    internal val settingStore = com.imyvm.iwg.domain.component.SettingStore(settings)

    init {
        require(isValidGeoName(name)) { "invalid region name" }
        replaceScopes(geometryScope)
        replaceLegacyOwnershipHistory(ownershipHistoryByScope)
    }

    /** Binary-compatible facade. A Region identity cannot be replaced after construction. */
    var numberID: Int
        get() = stableNumberId
        set(value) = require(value == stableNumberId) { "region id cannot be changed" }

    internal val scopes: List<GeoScope>
        get() = scopeView

    /**
     * Binary-compatible snapshot for existing addons.
     *
     * Mutating the returned list does not change this Region. Use the addon API for writes.
     */
    @set:Deprecated("Change Region Scopes through PlayerInteractionApi")
    var geometryScope: MutableList<GeoScope>
        get() = mutableScopes.toMutableList()
        set(@Suppress("UNUSED_PARAMETER") value) {
            error("region scopes must be changed through the application boundary")
        }

    /**
     * Binary-compatible snapshot for existing addons.
     *
     * The map and its entry lists are detached from this Region.
     */
    @set:Deprecated("Ownership history is maintained by Scope transfer and merge operations")
    var ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>>
        get() = mutableOwnershipHistory.entries.associateTo(mutableMapOf()) { (scopeId, entries) ->
            scopeId.raw to entries.toMutableList()
        }
        set(@Suppress("UNUSED_PARAMETER") value) {
            error("ownership history must be changed through the application boundary")
        }

    /**
     * Binary-compatible view for existing addons.
     *
     * The getter returns a detached snapshot. Mutating that list does not change this Region.
     * Use `RegionDataApi` for reads and `PlayerInteractionApi` setting operations for writes.
     * The JVM getter, setter, and constructor parameter are retained with no scheduled removal.
     */
    @set:Deprecated("Change Region settings through PlayerInteractionApi")
    var settings: MutableList<Setting>
        get() = settingStore.toLegacyList().toMutableList()
        set(@Suppress("UNUSED_PARAMETER") value) {
            error("settings must be changed through the application boundary")
        }

    fun getScopeByName(scopeName: String): GeoScope {
        return mutableScopes.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw ScopeNotFoundException(scopeName, name)
    }

    fun containsScope(scope: GeoScope): Boolean = mutableScopes.any { it === scope }

    @Deprecated("Use PlayerInteractionApi scope creation operations")
    fun addScope(@Suppress("UNUSED_PARAMETER") scope: GeoScope) {
        error("region scopes must be changed through the application boundary")
    }

    internal fun addOwnedScope(scope: GeoScope) {
        validateScope(scope, mutableScopes)
        mutableScopes.add(scope)
    }

    @Deprecated("Use PlayerInteractionApi scope deletion or transfer operations")
    fun removeScope(@Suppress("UNUSED_PARAMETER") scope: GeoScope): Int {
        error("region scopes must be changed through the application boundary")
    }

    internal fun removeOwnedScope(scope: GeoScope): ScopeRemovalReceipt {
        val index = mutableScopes.indexOfFirst { it === scope }
        require(index >= 0) { "scope does not belong to region" }
        require(mutableScopes.size > 1) { "region must contain at least one scope" }
        mutableScopes.removeAt(index)
        return ScopeRemovalReceipt(scope, index)
    }

    @Deprecated("Use PlayerInteractionApi scope deletion or transfer operations")
    fun restoreScope(@Suppress("UNUSED_PARAMETER") index: Int, @Suppress("UNUSED_PARAMETER") scope: GeoScope) {
        error("region scopes must be changed through the application boundary")
    }

    internal fun restoreOwnedScope(receipt: ScopeRemovalReceipt) {
        validateScope(receipt.scope, mutableScopes)
        require(receipt.index in 0..mutableScopes.size) { "scope removal receipt index is invalid" }
        mutableScopes.add(receipt.index, receipt.scope)
    }

    internal fun retireOwnedScopesForMerge(): List<GeoScope> {
        val retired = mutableScopes.toList()
        mutableScopes.clear()
        return retired
    }

    internal fun restoreOwnedScopes(scopes: List<GeoScope>) = replaceScopes(scopes)

    @Deprecated("Use PlayerInteractionApi scope rename operations")
    fun renameScope(@Suppress("UNUSED_PARAMETER") scope: GeoScope, @Suppress("UNUSED_PARAMETER") newName: String) {
        error("scope names must be changed through the application boundary")
    }

    internal fun renameOwnedScope(scope: GeoScope, newName: String) {
        require(containsScope(scope)) { "scope does not belong to region" }
        require(mutableScopes.none { it !== scope && it.scopeName.equals(newName, ignoreCase = true) }) {
            "duplicate scope name"
        }
        scope.renameTo(newName)
    }

    @Deprecated("Ownership history is maintained by scope transfer and merge operations")
    fun recordScopeOwnership(@Suppress("UNUSED_PARAMETER") entry: ScopeOwnershipEntry) {
        error("ownership history must be changed through the application boundary")
    }

    internal fun recordOwnedScopeOwnership(entry: ScopeOwnershipEntry) {
        require(entry.toRegionNumberId == numberID) { "ownership entry targets another region" }
        val updated = mutableOwnershipHistory[entry.scopeId].orEmpty() + entry
        validateOwnershipChain(entry.scopeId, updated)
        mutableOwnershipHistory[entry.scopeId] = updated.toMutableList()
    }

    internal fun ownershipHistory(scopeId: AssignedScopeId): List<ScopeOwnershipEntry> =
        mutableOwnershipHistory[scopeId].orEmpty()

    internal fun ownershipHistorySnapshot(): MutableMap<AssignedScopeId, MutableList<ScopeOwnershipEntry>> =
        mutableOwnershipHistory.mapValuesTo(mutableMapOf()) { (_, entries) -> entries.toMutableList() }

    internal fun replaceOwnershipHistory(value: Map<AssignedScopeId, List<ScopeOwnershipEntry>>) {
        value.forEach(::validateOwnershipChain)
        mutableOwnershipHistory.clear()
        value.forEach { (scopeId, entries) -> mutableOwnershipHistory[scopeId] = entries.toMutableList() }
    }

    internal fun renameTo(value: String) {
        require(isValidGeoName(value)) { "invalid region name" }
        currentName = value
    }

    internal fun setDynmapVisibility(value: Boolean) {
        currentShowOnDynmap = value
    }

    internal fun settingsSnapshot(): List<Setting> = settingStore.toLegacyList()

    private fun replaceLegacyOwnershipHistory(value: Map<Long, List<ScopeOwnershipEntry>>) {
        val typed = value.entries.associate { (raw, entries) ->
            val scopeId = AssignedScopeId.fromRaw(raw)
                ?: throw IllegalArgumentException("ownership history scope id is not assigned")
            require(entries.all { it.scopeId == scopeId }) { "ownership history key does not match entry" }
            scopeId to entries
        }
        replaceOwnershipHistory(typed)
    }

    private fun validateOwnershipChain(scopeId: AssignedScopeId, entries: List<ScopeOwnershipEntry>) {
        require(entries.isNotEmpty()) { "ownership history must not be empty" }
        require(entries.all { it.scopeId == scopeId }) { "ownership history key does not match entry" }
        for (index in 1 until entries.size) {
            val previous = entries[index - 1]
            val current = entries[index]
            require(previous.toRegionNumberId == current.fromRegionNumberId) { "ownership history chain is broken" }
            require(previous.changedAtMillis <= current.changedAtMillis) { "ownership history is out of order" }
        }
        require(entries.last().toRegionNumberId == numberID) { "ownership history does not end at its owner" }
    }

    private fun replaceScopes(scopes: List<GeoScope>) {
        require(scopes.isNotEmpty()) { "region must contain at least one scope" }
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
        return formatSettingInfos(server, settings, SettingPresentationTarget.RegionSettings)
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
        @Deprecated("Use the Region/GeoScope setting presentation entry points")
        fun formatSettings(
            server: MinecraftServer,
            settings: List<Setting>,
            key: String,
            scopeName: String? = null
        ): List<Component> {
            return formatSettingInfos(server, settings, legacySettingPresentationTarget(key, scopeName))
        }

        internal fun formatSettingInfos(
            server: MinecraftServer,
            settings: List<Setting>,
            target: SettingPresentationTarget
        ): List<Component> {
            if (settings.isEmpty()) return emptyList()

            val result = mutableListOf<Component>()
            val keys = target.keys

            result.add(target.translateHeader())

            val globalSettings = settings.filter { !it.isPersonal }
            if (globalSettings.isNotEmpty()) {
                result.add(Translator.tr(keys.globalHeader))
                appendTypeGroups(result, globalSettings, keys)
            }

            settings.filter { it.isPersonal }
                .groupBy { it.playerUUID }
                .forEach { (uuid, playerSettings) ->
                    val playerName = resolvePlayerName(server, uuid)
                    result.add(Translator.tr(keys.personalHeader, playerName))
                    appendTypeGroups(result, playerSettings, keys)
                }

            return result
        }

        private fun appendTypeGroups(
            result: MutableList<Component>,
            settings: List<Setting>,
            keys: SettingPresentationKeys
        ) {
            val permissions = settings.filter { it is PermissionSetting || it is ExtensionPermissionSetting }
            if (permissions.isNotEmpty()) {
                result.add(Translator.tr(keys.permissionHeader))
                permissions.forEach { setting -> result.add(Translator.tr(keys.item, setting.key, setting.value)) }
            }

            val effects = settings.filterIsInstance<EffectSetting>()
            if (effects.isNotEmpty()) {
                result.add(Translator.tr(keys.effectHeader))
                effects.forEach { setting -> result.add(Translator.tr(keys.item, setting.key, setting.value)) }
            }

            val rules = settings.filter { it is RuleSetting || it is ExtensionRuleSetting }
            if (rules.isNotEmpty()) {
                result.add(Translator.tr(keys.ruleHeader))
                rules.forEach { setting -> result.add(Translator.tr(keys.item, setting.key, setting.value)) }
            }

            val notifToggles = settings.filterIsInstance<EntryExitToggleSetting>()
            val notifMessages = settings.filterIsInstance<EntryExitMessageSetting>()
            if (notifToggles.isNotEmpty() || notifMessages.isNotEmpty()) {
                result.add(Translator.tr(keys.entryExitHeader))
                notifToggles.forEach { setting -> result.add(Translator.tr(keys.item, setting.key, setting.value)) }
                notifMessages.forEach { setting ->
                    result.add(Translator.tr(keys.item, setting.key, "&r\"${setting.value}\""))
                }
            }
        }
    }
}
