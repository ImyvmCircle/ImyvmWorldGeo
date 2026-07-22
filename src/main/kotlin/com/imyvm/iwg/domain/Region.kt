package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.GeoShape
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
import com.imyvm.iwg.util.geo.checkIntersection
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import kotlin.math.round

class ScopeNotFoundException(
    val scopeName: String,
    val regionName: String
) : IllegalArgumentException("region.error.no_scope")

class Region(
    name: String,
    numberID: Int,
    geometryScope: MutableList<GeoScope>,
    settings: MutableList<Setting> = mutableListOf(),
    subSpaces: MutableList<SubSpace> = mutableListOf(),
    var showOnDynmap: Boolean = true,
    ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>> = mutableMapOf()
) {
    constructor(
        name: String,
        numberID: Int,
        geometryScope: MutableList<GeoScope>,
        settings: MutableList<Setting> = mutableListOf(),
        showOnDynmap: Boolean = true,
        ownershipHistoryByScope: MutableMap<Long, MutableList<ScopeOwnershipEntry>> = mutableMapOf()
    ) : this(name, numberID, geometryScope, settings, mutableListOf(), showOnDynmap, ownershipHistoryByScope)

    var name: String = name
        set(value) {
            require(isValidGeoName(value)) { "invalid region name" }
            field = value
        }

    private val stableNumberId = numberID.also { require(it > 0) { "region id must be positive" } }
    private val mutableScopes = mutableListOf<GeoScope>()
    private val mutableSubSpaces = mutableListOf<SubSpace>()
    private val mutableOwnershipHistory = mutableMapOf<AssignedScopeId, MutableList<ScopeOwnershipEntry>>()
    internal val settingStore = com.imyvm.iwg.domain.component.SettingStore(settings)

    init {
        require(isValidGeoName(name)) { "invalid region name" }
        replaceScopes(geometryScope)
        replaceSubSpaces(subSpaces)
        replaceLegacyOwnershipHistory(ownershipHistoryByScope)
    }

    /** Binary-compatible facade. A Region identity cannot be replaced after construction. */
    var numberID: Int
        get() = stableNumberId
        set(value) = require(value == stableNumberId) { "region id cannot be changed" }

    internal val scopes: List<GeoScope>
        get() = mutableScopes

    val subSpaces: List<SubSpace>
        get() = mutableSubSpaces.toList()

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
            ?: throw ScopeNotFoundException(scopeName, name)
    }

    fun containsScope(scope: GeoScope): Boolean = mutableScopes.any { it === scope }

    fun containsSubSpace(subSpace: SubSpace): Boolean = mutableSubSpaces.any { it === subSpace }

    fun addScope(scope: GeoScope) {
        validateScope(scope, mutableScopes)
        mutableScopes.add(scope)
    }

    fun removeScope(scope: GeoScope): Int {
        val index = mutableScopes.indexOfFirst { it === scope }
        require(index >= 0) { "scope does not belong to region" }
        require(mutableSubSpaces.none { it.parentScopeId == scope.requireAssignedScopeId() }) { "scope has subspaces" }
        mutableScopes.removeAt(index)
        return index
    }

    fun restoreScope(index: Int, scope: GeoScope) {
        validateScope(scope, mutableScopes)
        mutableScopes.add(index.coerceIn(0, mutableScopes.size), scope)
    }

    internal fun restoreScopes(scopes: List<GeoScope>) = replaceScopes(scopes)

    fun addSubSpace(subSpace: SubSpace) {
        validateSubSpace(subSpace, mutableSubSpaces)
        mutableSubSpaces.add(subSpace)
    }

    fun removeSubSpace(subSpace: SubSpace): Int {
        val index = mutableSubSpaces.indexOfFirst { it === subSpace }
        require(index >= 0) { "subspace does not belong to region" }
        mutableSubSpaces.removeAt(index)
        return index
    }

    fun restoreSubSpace(index: Int, subSpace: SubSpace) {
        validateSubSpace(subSpace, mutableSubSpaces)
        mutableSubSpaces.add(index.coerceIn(0, mutableSubSpaces.size), subSpace)
    }

    internal fun replaceSubSpaces(subSpaces: List<SubSpace>) {
        val names = hashSetOf<String>()
        val ids = hashSetOf<Long>()
        subSpaces.forEach { subSpace ->
            require(names.add(subSpace.name.lowercase())) { "duplicate subspace name" }
            require(ids.add(subSpace.subSpaceId)) { "duplicate subspace id" }
            validateSubSpacePlacement(subSpace)
        }
        mutableSubSpaces.clear()
        mutableSubSpaces.addAll(subSpaces)
    }

    fun renameScope(scope: GeoScope, newName: String) {
        require(containsScope(scope)) { "scope does not belong to region" }
        require(mutableScopes.none { it !== scope && it.scopeName.equals(newName, ignoreCase = true) }) {
            "duplicate scope name"
        }
        scope.renameTo(newName)
    }

    fun renameSubSpace(subSpace: SubSpace, newName: String) {
        require(containsSubSpace(subSpace)) { "subspace does not belong to region" }
        require(mutableSubSpaces.none { it !== subSpace && it.name.equals(newName, ignoreCase = true) }) {
            "duplicate subspace name"
        }
        subSpace.renameTo(newName)
    }

    fun replaceScopeGeometry(scope: GeoScope, shape: GeoShape?) {
        require(containsScope(scope)) { "scope does not belong to region" }
        val oldShape = scope.geoShape
        scope.replaceGeometry(shape)
        try {
            mutableSubSpaces
                .filter { it.parentScopeId == scope.requireAssignedScopeId() }
                .forEach(::validateSubSpacePlacement)
        } catch (error: IllegalArgumentException) {
            scope.replaceGeometry(oldShape)
            throw error
        }
    }

    fun replaceSubSpaceGeometry(subSpace: SubSpace, shape: GeoShape) {
        require(containsSubSpace(subSpace)) { "subspace does not belong to region" }
        val oldShape = subSpace.geoShape
        subSpace.replaceGeometry(shape)
        try {
            validateSubSpacePlacement(subSpace)
        } catch (error: IllegalArgumentException) {
            subSpace.replaceGeometry(oldShape)
            throw error
        }
    }

    fun recordScopeOwnership(entry: ScopeOwnershipEntry) {
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

    private fun validateSubSpace(subSpace: SubSpace, existing: List<SubSpace>) {
        require(existing.none { it.name.equals(subSpace.name, ignoreCase = true) }) { "duplicate subspace name" }
        require(existing.none { it.subSpaceId == subSpace.subSpaceId }) { "duplicate subspace id" }
        validateSubSpacePlacement(subSpace)
    }

    private fun validateSubSpacePlacement(subSpace: SubSpace) {
        val parentScope = mutableScopes.firstOrNull { it.requireAssignedScopeId() == subSpace.parentScopeId }
            ?: throw IllegalArgumentException("subspace parent scope does not belong to region")
        require(parentScope.worldId == subSpace.worldId) { "subspace world must match parent scope" }
        val parentShape = parentScope.geoShape ?: throw IllegalArgumentException("subspace parent scope must have a shape")
        require(subSpace.geoShape.isContainedBy(parentShape)) { "subspace shape must be inside parent scope" }
        val siblingScopes = mutableSubSpaces
            .asSequence()
            .filter { it !== subSpace && it.parentScopeId == subSpace.parentScopeId }
            .map { sibling ->
                GeoScope(
                    sibling.name,
                    sibling.worldId,
                    null,
                    geoShape = sibling.geoShape,
                    scopeId = com.imyvm.iwg.domain.component.ScopeId(subSpace.parentScopeId.raw)
                ) to name
            }
            .toList()
        require(checkIntersection(subSpace.geoShape, siblingScopes).isEmpty()) { "subspace overlaps another subspace" }
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
