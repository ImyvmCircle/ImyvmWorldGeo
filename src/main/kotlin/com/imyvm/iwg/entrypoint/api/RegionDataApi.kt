package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.application.interaction.getDefaultValueForPermission
import com.imyvm.iwg.application.interaction.getDefaultValueForRule
import com.imyvm.iwg.application.interaction.getRegionPermissionValue
import com.imyvm.iwg.application.interaction.getScopePermissionValue
import com.imyvm.iwg.application.interaction.onCertificateExtensionPermissionValue
import com.imyvm.iwg.application.interaction.getEffectiveExtensionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveScopeRuleValue
import com.imyvm.iwg.application.interaction.onGettingTeleportPointAccessibility
import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.application.region.effect.helper.getRegionActiveEffects as resolveRegionActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getRegionEffectValue as resolveRegionEffectValue
import com.imyvm.iwg.application.region.effect.helper.getScopeActiveEffects as resolveScopeActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getScopeEffectValue as resolveScopeEffectValue
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.inter.api.helper.filterSettingsByType
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import java.util.*
@Suppress("unused")
object RegionDataApi {
    fun registerExtensionPermissionKey(key: String, defaultValue: Boolean = true) {
        ExtensionSettingRegistry.registerPermissionKey(key, defaultValue)
    }

    fun registerExtensionRuleKey(key: String, defaultValue: Boolean = true) {
        ExtensionSettingRegistry.registerRuleKey(key, defaultValue)
    }

    fun getRegisteredExtensionPermissionKeys(): List<String> =
        ExtensionSettingRegistry.getRegisteredPermissionKeys()

    fun getRegisteredExtensionRuleKeys(): List<String> =
        ExtensionSettingRegistry.getRegisteredRuleKeys()

    fun getRegion(id: Int): Region? {
        return try {
            RegionDatabase.getRegionByNumberId(id)
        } catch (e: RegionNotFoundException) {
            null
        }
    }

    fun getRegionByName(name: String): Region? {
        return try {
            RegionDatabase.getRegionByName(name)
        } catch (e: RegionNotFoundException) {
            null
        }
    }

    fun getRegionList(): List<Region> = RegionDatabase.getRegionList().toList()

    fun getRegionListFiltered(idMark: Int): List<Region> = filterRegionsByMark(idMark)

    fun getRegionFoundingTime(region: Region): Long =
        parseFoundingTimeFromRegionId(region.numberID)

    fun getRegionAge(region: Region): Long =
        System.currentTimeMillis() - parseFoundingTimeFromRegionId(region.numberID)

    fun getRegionScopes(region: Region): List<GeoScope> =
        region.geometryScope.toList()

    fun getRegionScopePair(region: Region, scopeName: String): Pair<Region, GeoScope?> =
        RegionDatabase.getRegionAndScope(region, scopeName)

    fun getRegionScopePair(regionId: Int, scopeName: String): Pair<Region?, GeoScope?> =
        RegionDatabase.getRegionAndScope(regionId, scopeName)

    fun getRegionScopePairByLocation(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, x, z)

    fun getRegionScopePairByLocation(world: Level, blockPos: BlockPos): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, blockPos.x, blockPos.z)
    fun inquireTeleportPointAccessibility(scope: GeoScope) = onGettingTeleportPointAccessibility(scope)

    fun getScopeTeleportPoint(scope: GeoScope): BlockPos? = scope.teleportPoint

    fun getScopeShape(scope: GeoScope): GeoShape? = scope.geoShape

    fun getScopeArea(scope: GeoScope): Double? =
        scope.geoShape?.calculateArea()

    fun getRegionArea(region: Region): Double = region.calculateTotalArea()

    fun getRegionGlobalSettings(region: Region): List<Setting> =
        region.settings.toSet().filter { !it.isPersonal }

    fun getRegionGlobalSettingsByType(region: Region, settingTypes: SettingTypes): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = false)

    fun getRegionPersonalSettings(region: Region, playerUUID: UUID,): List<Setting> =
        region.settings.toSet().filter { it.isPersonal && it.playerUUID == playerUUID }

    fun getRegionPersonalSettingsByType(
        region: Region,
        playerUUID: UUID,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = true, playerUUID = playerUUID)

    fun getScopeGlobalSettings(scope: GeoScope): List<Setting> =
        scope.settings.toSet().filter { !it.isPersonal }

    fun getScopeGlobalSettingsByType(
        scope: GeoScope,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = false)

    fun getScopePersonalSettings(scope: GeoScope, playerUUID: UUID): List<Setting> =
        scope.settings.toSet().filter { it.isPersonal && it.playerUUID == playerUUID }

    fun getScopePersonalSettingsByType(
        scope: GeoScope,
        playerUUID: UUID,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = true, playerUUID = playerUUID)

    fun getDefaultPermissionValue(permissionKey: PermissionKey): Boolean =
        getDefaultValueForPermission(permissionKey)

    fun getRegionGlobalPermissionValue(region: Region, permissionKey: PermissionKey): Boolean =
        getRegionPermissionValue(region, permissionKey)

    fun getRegionPlayerPermissionValue(region: Region, playerUUID: UUID, permissionKey: PermissionKey): Boolean =
        getRegionPermissionValue(region, playerUUID, permissionKey)

    fun getScopeGlobalPermissionValue(region: Region, scope: GeoScope, permissionKey: PermissionKey): Boolean =
        getScopePermissionValue(region, scope, permissionKey)

    fun getScopePlayerPermissionValue(
        region: Region,
        scope: GeoScope,
        playerUUID: UUID,
        permissionKey: PermissionKey
    ): Boolean = getScopePermissionValue(region, scope, playerUUID, permissionKey)

    @Deprecated("Use an explicit default, region, or scope permission query")
    fun getPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, permissionKey: PermissionKey): Boolean {
        if (region == null) {
            require(scope == null) { "scope requires region" }
            return getDefaultPermissionValue(permissionKey)
        }
        return when {
            scope != null && playerUUID != null -> getScopePlayerPermissionValue(region, scope, playerUUID, permissionKey)
            scope != null -> getScopeGlobalPermissionValue(region, scope, permissionKey)
            playerUUID != null -> getRegionPlayerPermissionValue(region, playerUUID, permissionKey)
            else -> getRegionGlobalPermissionValue(region, permissionKey)
        }
    }

    fun getDefaultExtensionPermissionValue(key: String): Boolean =
        onCertificateExtensionPermissionValue(null, null, null, key)

    fun getRegionGlobalExtensionPermissionValue(region: Region, key: String): Boolean =
        onCertificateExtensionPermissionValue(region, null, null, key)

    fun getRegionPlayerExtensionPermissionValue(region: Region, playerUUID: UUID, key: String): Boolean =
        onCertificateExtensionPermissionValue(region, null, playerUUID, key)

    fun getScopeGlobalExtensionPermissionValue(region: Region, scope: GeoScope, key: String): Boolean =
        onCertificateExtensionPermissionValue(region, scope, null, key)

    fun getScopePlayerExtensionPermissionValue(
        region: Region,
        scope: GeoScope,
        playerUUID: UUID,
        key: String
    ): Boolean = onCertificateExtensionPermissionValue(region, scope, playerUUID, key)

    @Deprecated("Use an explicit default, region, or scope extension permission query")
    fun getExtensionPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, key: String): Boolean {
        if (region == null) {
            require(scope == null) { "scope requires region" }
            return getDefaultExtensionPermissionValue(key)
        }
        return when {
            scope != null && playerUUID != null -> getScopePlayerExtensionPermissionValue(region, scope, playerUUID, key)
            scope != null -> getScopeGlobalExtensionPermissionValue(region, scope, key)
            playerUUID != null -> getRegionPlayerExtensionPermissionValue(region, playerUUID, key)
            else -> getRegionGlobalExtensionPermissionValue(region, key)
        }
    }

    fun getDefaultRuleValue(ruleKey: RuleKey): Boolean = getDefaultValueForRule(ruleKey)

    fun getRegionRuleValue(region: Region, ruleKey: RuleKey): Boolean =
        getEffectiveRegionRuleValue(region, ruleKey)

    fun getScopeRuleValue(region: Region, scope: GeoScope, ruleKey: RuleKey): Boolean =
        getEffectiveScopeRuleValue(region, scope, ruleKey)

    @Deprecated("Use an explicit default, region, or scope rule query")
    fun getRuleValueForRegion(region: Region?, scope: GeoScope?, ruleKey: RuleKey): Boolean {
        if (region == null) {
            require(scope == null) { "scope requires region" }
            return getDefaultRuleValue(ruleKey)
        }
        return if (scope == null) getRegionRuleValue(region, ruleKey) else getScopeRuleValue(region, scope, ruleKey)
    }

    fun getExtensionRuleValueForRegion(region: Region?, scope: GeoScope?, key: String): Boolean {
        return getEffectiveExtensionRuleValue(region, scope, key)
    }

    fun getRegionEffectValue(region: Region, playerUUID: UUID, effectKey: EffectKey): Int? =
        resolveRegionEffectValue(region, playerUUID, effectKey)

    fun getScopeEffectValue(region: Region, scope: GeoScope, playerUUID: UUID, effectKey: EffectKey): Int? =
        resolveScopeEffectValue(region, scope, playerUUID, effectKey)

    fun getRegionActiveEffects(region: Region, playerUUID: UUID): Map<EffectKey, Int> =
        resolveRegionActiveEffects(region, playerUUID)

    fun getScopeActiveEffects(region: Region, scope: GeoScope, playerUUID: UUID): Map<EffectKey, Int> =
        resolveScopeActiveEffects(region, scope, playerUUID)

    @Deprecated("Use an explicit region or scope effect query")
    fun getEffectValueForRegion(region: Region?, scope: GeoScope?, playerUUID: UUID, effectKey: EffectKey): Int? {
        if (region == null) {
            require(scope == null) { "scope requires region" }
            return null
        }
        return if (scope == null) getRegionEffectValue(region, playerUUID, effectKey)
        else getScopeEffectValue(region, scope, playerUUID, effectKey)
    }

    @Deprecated("Use an explicit region or scope effect query")
    fun getActiveEffectsForRegion(region: Region, scope: GeoScope?, playerUUID: UUID): Map<EffectKey, Int> =
        if (scope == null) getRegionActiveEffects(region, playerUUID)
        else getScopeActiveEffects(region, scope, playerUUID)

    fun getRegionScopeCount(region: Region): Int = region.geometryScope.size

    fun getRegionNaturalStats(server: MinecraftServer, region: Region): RegionNaturalStatsResult =
        RegionNaturalStatsCollector.collectRegionStats(server, region)

    fun getScopeNaturalStats(server: MinecraftServer, scope: GeoScope): RegionNaturalStatsResult =
        RegionNaturalStatsCollector.collectScopeStats(server, scope)

    fun getRegionPlayerStats(region: Region): RegionPlayerStats =
        RegionDatabase.getRegionPlayerStats(region)

    fun getRegionEntryExitToggle(region: Region): Boolean =
        region.settings.filterIsInstance<EntryExitToggleSetting>().firstOrNull { it.key == EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED }?.value ?: true

    fun getRegionEntryExitMessage(region: Region, key: EntryExitMessageKey): String? =
        region.settings.filterIsInstance<EntryExitMessageSetting>().firstOrNull { it.key == key }?.value

    fun getScopeEntryExitToggle(scope: GeoScope): Boolean =
        scope.settings.filterIsInstance<EntryExitToggleSetting>().firstOrNull { it.key == EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED }?.value ?: true

    fun getScopeEntryExitMessage(scope: GeoScope, key: EntryExitMessageKey): String? =
        scope.settings.filterIsInstance<EntryExitMessageSetting>().firstOrNull { it.key == key }?.value

    // --- 1.5.1 additions ---

    fun parseScopeId(s: String): ScopeId? = ScopeId.parse(s)

    fun getScopeById(scopeId: ScopeId): Pair<Region, GeoScope>? = RegionDatabase.getScopeById(scopeId)

    fun getScopeId(scope: GeoScope): ScopeId = scope.scopeId

    fun getScopeFoundingTimeOrNull(scope: GeoScope): Long? = scope.scopeId.creationTimeMillisOrNull()

    fun getScopeFoundedInRegionNumberId(scope: GeoScope): Int = scope.scopeId.foundedInRegionNumberId()

    fun getScopeOwnershipHistory(scopeId: ScopeId): List<com.imyvm.iwg.domain.ScopeOwnershipEntry> =
        RegionDatabase.getScopeOwnershipHistory(scopeId)

    fun resolveScopeAtEntity(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, x, z)

    fun resolveScopeAtEntity(world: Level, blockPos: BlockPos): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, blockPos.x, blockPos.z)

    fun getEffectiveEffectsForScope(region: Region, scope: GeoScope): Map<EffectKey, Int> {
        val keys = mutableSetOf<EffectKey>()
        scope.settings.filterIsInstance<EffectSetting>().filter { !it.isPersonal }.forEach { keys.add(it.key) }
        region.settings.filterIsInstance<EffectSetting>().filter { !it.isPersonal }.forEach { keys.add(it.key) }
        val overlay = if (scope.scopeId.raw != ScopeId.UNASSIGNED_RAW)
            com.imyvm.iwg.application.region.effect.EffectOverlayService.queryOverlay(scope.scopeId)
        else emptyMap()
        keys.addAll(overlay.keys)

        val result = mutableMapOf<EffectKey, Int>()
        for (key in keys) {
            val overlayValue = overlay[key]
            if (overlayValue != null) {
                result[key] = overlayValue
                continue
            }
            val scopeValue = scope.settings.filterIsInstance<EffectSetting>()
                .firstOrNull { !it.isPersonal && it.key == key }?.value
            if (scopeValue != null) {
                result[key] = scopeValue
                continue
            }
            val regionValue = region.settings.filterIsInstance<EffectSetting>()
                .firstOrNull { !it.isPersonal && it.key == key }?.value
            if (regionValue != null) result[key] = regionValue
        }
        return result
    }

    fun getEffectiveRulesForScope(region: Region, scope: GeoScope): Map<RuleKey, Boolean> {
        val keys = mutableSetOf<RuleKey>()
        scope.settings.filterIsInstance<RuleSetting>().forEach { keys.add(it.key) }
        region.settings.filterIsInstance<RuleSetting>().forEach { keys.add(it.key) }
        val result = mutableMapOf<RuleKey, Boolean>()
        for (key in keys) {
            result[key] = getEffectiveScopeRuleValue(region, scope, key)
        }
        return result
    }

    fun applyTimedEffectOverlay(overlay: com.imyvm.iwg.domain.TimedEffectOverlay): String =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.applyTimedEffectOverlay(overlay)

    fun clearTimedEffectOverlay(scopeId: ScopeId, overlayId: String): Boolean =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.clearTimedEffectOverlay(scopeId, overlayId)

    fun queryOverlay(scopeId: ScopeId): Map<EffectKey, Int> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryOverlay(scopeId)

    fun queryActiveOverlays(scopeId: ScopeId): List<com.imyvm.iwg.domain.TimedEffectOverlay> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryActiveOverlays(scopeId)
}
