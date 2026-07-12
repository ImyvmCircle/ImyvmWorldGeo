package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.application.interaction.onCertificatePermissionValue
import com.imyvm.iwg.application.interaction.onCertificateExtensionPermissionValue
import com.imyvm.iwg.application.interaction.onCertificateRuleValue
import com.imyvm.iwg.application.interaction.getEffectiveExtensionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveRuleValue
import com.imyvm.iwg.application.interaction.onGettingTeleportPointAccessibility
import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.application.region.effect.helper.getActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getEffectValue
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

    fun getPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, permissionKey: PermissionKey): Boolean {
        return onCertificatePermissionValue(region, scope, playerUUID, permissionKey)
    }

    fun getExtensionPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, key: String): Boolean {
        return onCertificateExtensionPermissionValue(region, scope, playerUUID, key)
    }

    fun getRuleValueForRegion(region: Region?, scope: GeoScope?, ruleKey: RuleKey): Boolean {
        return getEffectiveRuleValue(region, ruleKey, scope)
    }

    fun getExtensionRuleValueForRegion(region: Region?, scope: GeoScope?, key: String): Boolean {
        return getEffectiveExtensionRuleValue(region, scope, key)
    }

    fun getEffectValueForRegion(region: Region?, scope: GeoScope?, playerUUID: UUID, effectKey: EffectKey): Int? =
        getEffectValue(region, playerUUID, effectKey, scope)

    fun getActiveEffectsForRegion(region: Region, scope: GeoScope?, playerUUID: UUID): Map<EffectKey, Int> =
        getActiveEffects(region, playerUUID, scope)

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
            result[key] = getEffectiveRuleValue(region, key, scope)
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
