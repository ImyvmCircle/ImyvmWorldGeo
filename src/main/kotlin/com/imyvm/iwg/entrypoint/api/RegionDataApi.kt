package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.event.WorldGeoBehaviorEventBus
import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.application.region.WorldGeoGeographicProfileSupport
import com.imyvm.iwg.application.space.WorldGeoSpaceSupport
import com.imyvm.iwg.application.time.WorldGeoPeriodTracker
import com.imyvm.iwg.application.time.WorldGeoTimeService
import com.imyvm.iwg.application.interaction.getDefaultValueForPermission
import com.imyvm.iwg.application.interaction.getDefaultValueForRule
import com.imyvm.iwg.application.interaction.getRegionPermissionValue
import com.imyvm.iwg.application.interaction.getScopePermissionValue
import com.imyvm.iwg.application.interaction.getSubSpacePermissionValue
import com.imyvm.iwg.application.interaction.onCertificateExtensionPermissionValue
import com.imyvm.iwg.application.interaction.getEffectiveExtensionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveScopeRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveSubSpaceRuleValue
import com.imyvm.iwg.application.interaction.onGettingTeleportPointAccessibility
import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.application.region.effect.helper.getRegionActiveEffects as resolveRegionActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getRegionEffectValue as resolveRegionEffectValue
import com.imyvm.iwg.application.region.effect.helper.getScopeActiveEffects as resolveScopeActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getSubSpaceActiveEffects as resolveSubSpaceActiveEffects
import com.imyvm.iwg.application.region.effect.helper.getScopeEffectValue as resolveScopeEffectValue
import com.imyvm.iwg.application.region.effect.helper.getSubSpaceEffectValue as resolveSubSpaceEffectValue
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.inter.api.helper.filterSettingsByType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.*
import java.util.function.Consumer
/**
 * Supported read/query API for addons.
 *
 * Compatibility and deprecation policy: `docs/addon-api-compatibility.md`.
 * Prefer explicit Region/Scope and global/player methods over nullable dispatchers.
 */
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
        region.scopes.toList()

    fun getRegionSubSpaces(region: Region): List<SubSpace> =
        region.subSpaces.toList()

    fun listScopeSnapshots(regionId: Int): List<WorldGeoSpaceSnapshot> {
        val region = getRegion(regionId) ?: return emptyList()
        return region.scopes.map { WorldGeoSpaceSupport.snapshot(region, it) }
    }

    fun listSubSpaceSnapshots(scopeId: Long): List<WorldGeoSpaceSnapshot> {
        val (region, scope, subSpaces) = findScopeWithSubSpaces(scopeId) ?: return emptyList()
        return subSpaces.map { WorldGeoSpaceSupport.snapshot(region, scope, it) }
    }

    fun getSpaceSnapshot(type: WorldGeoSpaceType, id: Long): WorldGeoSpaceSnapshot? = when (type) {
        WorldGeoSpaceType.REGION -> getRegion(id.toInt())?.let { WorldGeoSpaceSupport.snapshot(it) }
        WorldGeoSpaceType.GEOSCOPE -> getRegionByScopeId(id)?.let { (region, scope) -> WorldGeoSpaceSupport.snapshot(region, scope) }
        WorldGeoSpaceType.SUBSPACE -> getSubSpaceById(id)?.let { (region, scope, subSpace) -> WorldGeoSpaceSupport.snapshot(region, scope, subSpace) }
    }

    fun getSubSpaceSnapshotByName(scopeId: Long, name: String): WorldGeoSpaceSnapshot? {
        val (region, scope, subSpaces) = findScopeWithSubSpaces(scopeId) ?: return null
        val subSpace = subSpaces.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return WorldGeoSpaceSupport.snapshot(region, scope, subSpace)
    }

    fun getSubSpaceById(subSpaceId: Long): Triple<Region, GeoScope, SubSpace>? =
        RegionDatabase.getSubSpaceById(subSpaceId)

    fun getSubSpaceByName(region: Region, name: String): Pair<GeoScope, SubSpace>? =
        RegionDatabase.getSubSpaceByName(region, name)

    fun getRegionScopePair(region: Region, scopeName: String): Pair<Region, GeoScope?> =
        RegionDatabase.getRegionAndScope(region, scopeName)

    fun getRegionScopePair(regionId: Int, scopeName: String): Pair<Region?, GeoScope?> =
        RegionDatabase.getRegionAndScope(regionId, scopeName)

    fun getRegionScopePairByLocation(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, x, z)

    fun getRegionScopePairByLocation(world: Level, blockPos: BlockPos): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, blockPos.x, blockPos.z)

    fun getRegionScopeSubSpaceByLocation(world: Level, x: Int, z: Int): Triple<Region, GeoScope, SubSpace?>? =
        RegionDatabase.getRegionScopeSubSpaceAt(world, x, z)

    fun getRegionScopeSubSpaceByLocation(world: Level, blockPos: BlockPos): Triple<Region, GeoScope, SubSpace?>? =
        RegionDatabase.getRegionScopeSubSpaceAt(world, blockPos.x, blockPos.z)
    private fun getRegionByScopeId(scopeId: Long): Pair<Region, GeoScope>? {
        for (region in RegionDatabase.getRegionList()) {
            val scope = region.scopes.firstOrNull { it.assignedScopeIdOrNull?.raw == scopeId } ?: continue
            return region to scope
        }
        return null
    }

    private fun findScopeWithSubSpaces(scopeId: Long): Triple<Region, GeoScope, List<SubSpace>>? {
        val (region, scope) = getRegionByScopeId(scopeId) ?: return null
        val assigned = scope.requireAssignedScopeId()
        return Triple(region, scope, region.subSpaces.filter { it.parentScopeId == assigned })
    }

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

    fun getSubSpaceGlobalPermissionValue(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        permissionKey: PermissionKey
    ): Boolean = getSubSpacePermissionValue(region, scope, subSpace, permissionKey)

    fun getSubSpacePlayerPermissionValue(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        playerUUID: UUID,
        permissionKey: PermissionKey
    ): Boolean = getSubSpacePermissionValue(region, scope, subSpace, playerUUID, permissionKey)

    /**
     * Compatibility dispatcher for the former nullable permission API.
     *
     * @deprecated Since 26.2-1.5.2. Use the explicit default/Region/Scope and
     * global/player methods. Eligible for removal only after two released versions
     * and explicit maintainer approval.
     */
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

    fun getSubSpaceGlobalExtensionPermissionValue(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        key: String
    ): Boolean {
        if (!ExtensionSettingRegistry.isRegisteredPermissionKey(key)) {
            throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
        }
        return getSubSpacePermissionValue(region, scope, subSpace, ExtensionSettingRegistry.permissionKey(key))
    }

    fun getSubSpacePlayerExtensionPermissionValue(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        playerUUID: UUID,
        key: String
    ): Boolean {
        if (!ExtensionSettingRegistry.isRegisteredPermissionKey(key)) {
            throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
        }
        return getSubSpacePermissionValue(region, scope, subSpace, playerUUID, ExtensionSettingRegistry.permissionKey(key))
    }

    /**
     * Compatibility dispatcher for extension permissions.
     *
     * @deprecated Since 26.2-1.5.2. Use the explicit extension permission methods.
     * Eligible for removal only after two released versions and explicit maintainer approval.
     */
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

    fun getSubSpaceRuleValue(region: Region, scope: GeoScope, subSpace: SubSpace, ruleKey: RuleKey): Boolean =
        getEffectiveSubSpaceRuleValue(region, scope, subSpace, ruleKey)

    /**
     * Compatibility dispatcher for the former nullable rule API.
     *
     * @deprecated Since 26.2-1.5.2. Use [getDefaultRuleValue], [getRegionRuleValue],
     * or [getScopeRuleValue]. Eligible for removal only after two released versions and
     * explicit maintainer approval.
     */
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

    fun getSubSpaceEffectValue(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        playerUUID: UUID,
        effectKey: EffectKey
    ): Int? = resolveSubSpaceEffectValue(region, scope, subSpace, playerUUID, effectKey)

    fun getRegionActiveEffects(region: Region, playerUUID: UUID): Map<EffectKey, Int> =
        resolveRegionActiveEffects(region, playerUUID)

    fun getScopeActiveEffects(region: Region, scope: GeoScope, playerUUID: UUID): Map<EffectKey, Int> =
        resolveScopeActiveEffects(region, scope, playerUUID)

    fun getSubSpaceActiveEffects(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        playerUUID: UUID
    ): Map<EffectKey, Int> = resolveSubSpaceActiveEffects(region, scope, subSpace, playerUUID)

    /**
     * Compatibility dispatcher for one effect value.
     *
     * @deprecated Since 26.2-1.5.2. Use [getRegionEffectValue] or [getScopeEffectValue].
     * Eligible for removal only after two released versions and explicit maintainer approval.
     */
    @Deprecated("Use an explicit region or scope effect query")
    fun getEffectValueForRegion(region: Region?, scope: GeoScope?, playerUUID: UUID, effectKey: EffectKey): Int? {
        if (region == null) {
            require(scope == null) { "scope requires region" }
            return null
        }
        return if (scope == null) getRegionEffectValue(region, playerUUID, effectKey)
        else getScopeEffectValue(region, scope, playerUUID, effectKey)
    }

    /**
     * Compatibility dispatcher for active effects.
     *
     * @deprecated Since 26.2-1.5.2. Use [getRegionActiveEffects] or [getScopeActiveEffects].
     * Eligible for removal only after two released versions and explicit maintainer approval.
     */
    @Deprecated("Use an explicit region or scope effect query")
    fun getActiveEffectsForRegion(region: Region, scope: GeoScope?, playerUUID: UUID): Map<EffectKey, Int> =
        if (scope == null) getRegionActiveEffects(region, playerUUID)
        else getScopeActiveEffects(region, scope, playerUUID)

    fun getRegionScopeCount(region: Region): Int = region.scopes.size

    fun getRegionSpaceSnapshot(region: Region): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(region)

    fun getRegionSpaceSnapshot(server: MinecraftServer, region: Region): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(server, region)

    fun getScopeSpaceSnapshot(region: Region, scope: GeoScope): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(region, scope)

    fun getScopeSpaceSnapshot(server: MinecraftServer, region: Region, scope: GeoScope): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(server, region, scope)

    fun getSubSpaceSnapshot(region: Region, scope: GeoScope, subSpace: SubSpace): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(region, scope, subSpace)

    fun getSubSpaceSnapshot(server: MinecraftServer, region: Region, scope: GeoScope, subSpace: SubSpace): WorldGeoSpaceSnapshot =
        WorldGeoSpaceSupport.snapshot(server, region, scope, subSpace)

    fun getRegionGeographicProfile(server: MinecraftServer, region: Region): WorldGeoGeographicProfileResult =
        WorldGeoGeographicProfileSupport.profile(server, region)

    fun getRegionGeographicProfileSnapshot(server: MinecraftServer, region: Region): WorldGeoGeographicProfileSnapshot =
        WorldGeoGeographicProfileSupport.profileSnapshot(server, region)

    fun getScopeGeographicProfile(server: MinecraftServer, region: Region, scope: GeoScope): WorldGeoGeographicProfileResult =
        WorldGeoGeographicProfileSupport.profile(server, region, scope)

    fun getScopeGeographicProfileSnapshot(server: MinecraftServer, region: Region, scope: GeoScope): WorldGeoGeographicProfileSnapshot =
        WorldGeoGeographicProfileSupport.profileSnapshot(server, region, scope)

    fun getSubSpaceGeographicProfile(
        server: MinecraftServer,
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace
    ): WorldGeoGeographicProfileResult = WorldGeoGeographicProfileSupport.profile(server, region, scope, subSpace)

    fun getSubSpaceGeographicProfileSnapshot(
        server: MinecraftServer,
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace
    ): WorldGeoGeographicProfileSnapshot = WorldGeoGeographicProfileSupport.profileSnapshot(server, region, scope, subSpace)

    fun getGeographicProfileCacheStatus(): WorldGeoGeographicProfileCacheStatus =
        WorldGeoGeographicProfileSupport.cacheStatus()

    fun refreshGeographicProfiles(server: MinecraftServer): Int =
        WorldGeoGeographicProfileSupport.refreshAll(server, RegionDatabase.getRegionList())

    fun listRegionSettingSummaries(
        region: Region,
        visibility: WorldGeoSettingVisibility
    ): List<WorldGeoSettingSummary> = WorldGeoSpaceSupport.settingSummaries(region, visibility)

    fun listScopeSettingSummaries(
        region: Region,
        scope: GeoScope,
        visibility: WorldGeoSettingVisibility
    ): List<WorldGeoSettingSummary> {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        return WorldGeoSpaceSupport.settingSummaries(scope, visibility)
    }

    fun listSubSpaceSettingSummaries(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        visibility: WorldGeoSettingVisibility
    ): List<WorldGeoSettingSummary> {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        require(region.containsSubSpace(subSpace)) { "subspace does not belong to region" }
        require(subSpace.parentScopeId == scope.requireAssignedScopeId()) { "subspace parent scope does not match" }
        return WorldGeoSpaceSupport.settingSummaries(subSpace, visibility)
    }

    fun sendRegionSpaceMessage(server: MinecraftServer, region: Region, message: Component): Int =
        WorldGeoSpaceSupport.sendMessage(server, region, message)

    fun sendScopeSpaceMessage(server: MinecraftServer, region: Region, scope: GeoScope, message: Component): Int =
        WorldGeoSpaceSupport.sendMessage(server, region, scope, message)

    fun sendSubSpaceMessage(
        server: MinecraftServer,
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        message: Component
    ): Int = WorldGeoSpaceSupport.sendMessage(server, region, scope, subSpace, message)

    fun getRegionNaturalStats(server: MinecraftServer, region: Region): RegionNaturalStatsResult =
        RegionNaturalStatsCollector.collectRegionStats(server, region)

    fun getTimeSnapshot(level: ServerLevel): WorldGeoTimeSnapshot =
        WorldGeoTimeService.snapshot(level)

    fun getCurrentNaturalPeriodIds(): Map<NaturalPeriodKind, String> =
        WorldGeoPeriodTracker.currentPeriodIds()

    fun registerNaturalPeriodTransitionCallback(callback: Consumer<NaturalPeriodTransition>) {
        WorldGeoPeriodTracker.registerCallback { callback.accept(it) }
    }

    fun registerBehaviorEventCallback(callback: Consumer<WorldGeoBehaviorEvent>) {
        WorldGeoBehaviorEventBus.registerCallback { callback.accept(it) }
    }

    fun getRecentBehaviorEvents(): List<WorldGeoBehaviorEvent> =
        WorldGeoBehaviorEventBus.getRecentEvents()

    fun getRecentBehaviorEvents(limit: Int): List<WorldGeoBehaviorEvent> =
        WorldGeoBehaviorEventBus.getRecentEvents(limit)

    fun queryBehaviorStats(query: WorldGeoBehaviorStatsQuery): List<WorldGeoBehaviorStatsEntry> =
        BehaviorStatsStore.query(query)

    fun queryBehaviorStats(
        periodKind: NaturalPeriodKind,
        periodId: String,
        behaviorType: WorldGeoBehaviorType?,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID?,
        objectId: String?
    ): List<WorldGeoBehaviorStatsEntry> = queryBehaviorStats(
        periodKind, periodId, behaviorType, regionId, scopeId, subSpaceId, playerUuid, objectId, null
    )

    fun queryBehaviorStats(
        periodKind: NaturalPeriodKind,
        periodId: String,
        behaviorType: WorldGeoBehaviorType?,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID?,
        objectId: String?,
        targetId: String?
    ): List<WorldGeoBehaviorStatsEntry> = BehaviorStatsStore.query(
        WorldGeoBehaviorStatsQuery(periodKind, periodId, behaviorType, regionId, scopeId, subSpaceId, playerUuid, objectId, targetId)
    )

    fun queryBlockDelta(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        blockFilter: String?
    ): WorldGeoBlockDeltaStats = BehaviorStatsStore.queryBlockDelta(periodKind, periodId, regionId, scopeId, subSpaceId, blockFilter)

    fun queryResidence(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?
    ): WorldGeoResidenceStats = BehaviorStatsStore.queryResidence(periodKind, periodId, regionId, scopeId, subSpaceId)

    fun queryEntityCombat(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        objectFilter: String?
    ): WorldGeoEntityCombatStats = BehaviorStatsStore.queryEntityCombat(periodKind, periodId, regionId, scopeId, subSpaceId, objectFilter)

    fun queryEntityCombat(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        objectFilter: String?,
        targetFilter: String?
    ): WorldGeoEntityCombatStats = BehaviorStatsStore.queryEntityCombat(periodKind, periodId, regionId, scopeId, subSpaceId, objectFilter, targetFilter)

    fun queryOnlineTime(
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int?,
        scopeId: Long?,
        subSpaceId: Long?,
        playerUuid: UUID?
    ): WorldGeoOnlineTimeStats = BehaviorStatsStore.queryOnlineTime(periodKind, periodId, regionId, scopeId, subSpaceId, playerUuid)

    fun getScopeNaturalStats(server: MinecraftServer, scope: GeoScope): RegionNaturalStatsResult =
        RegionNaturalStatsCollector.collectScopeStats(server, scope)

    fun getSubSpaceNaturalStats(server: MinecraftServer, subSpace: SubSpace): RegionNaturalStatsResult =
        RegionNaturalStatsCollector.collectSubSpaceStats(server, subSpace)

    fun getRegionPlayerStats(region: Region): RegionPlayerStats =
        RegionDatabase.getRegionPlayerStats(region)

    fun getRegionEntryExitToggle(region: Region): Boolean =
        region.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED) ?: true

    fun getRegionEntryExitMessage(region: Region, key: EntryExitMessageKey): String? =
        region.settingStore.entryExitMessage(key)

    fun getScopeEntryExitToggle(scope: GeoScope): Boolean =
        scope.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED) ?: true

    fun getScopeEntryExitMessage(scope: GeoScope, key: EntryExitMessageKey): String? =
        scope.settingStore.entryExitMessage(key)

    // --- 1.5.1 additions ---

    fun parseAssignedScopeId(s: String): AssignedScopeId? = AssignedScopeId.parse(s)

    @Deprecated("Use parseAssignedScopeId")
    fun parseScopeId(s: String): ScopeId? = parseAssignedScopeId(s)?.toLegacyScopeId()

    fun getScopeByAssignedId(scopeId: AssignedScopeId): Pair<Region, GeoScope>? =
        RegionDatabase.getScopeByAssignedId(scopeId)

    @Deprecated("Use getScopeByAssignedId")
    fun getScopeById(scopeId: ScopeId): Pair<Region, GeoScope>? = RegionDatabase.getScopeById(scopeId)

    fun getAssignedScopeIdOrNull(scope: GeoScope): AssignedScopeId? = scope.assignedScopeIdOrNull

    @Deprecated("Use getAssignedScopeIdOrNull")
    fun getScopeId(scope: GeoScope): ScopeId = scope.scopeId

    fun getScopeFoundingTimeOrNull(scope: GeoScope): Long? =
        scope.assignedScopeIdOrNull?.toLegacyScopeId()?.creationTimeMillisOrNull()

    fun getScopeFoundedInRegionNumberId(scope: GeoScope): Int =
        scope.requireAssignedScopeId().toLegacyScopeId().foundedInRegionNumberId()

    fun getAssignedScopeOwnershipHistory(scopeId: AssignedScopeId): List<ScopeOwnershipEntry> =
        RegionDatabase.getAssignedScopeOwnershipHistory(scopeId)

    @Deprecated("Use getAssignedScopeOwnershipHistory")
    fun getScopeOwnershipHistory(scopeId: ScopeId): List<com.imyvm.iwg.domain.ScopeOwnershipEntry> =
        RegionDatabase.getScopeOwnershipHistory(scopeId)

    fun resolveScopeAtEntity(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, x, z)

    fun resolveScopeAtEntity(world: Level, blockPos: BlockPos): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(world, blockPos.x, blockPos.z)

    fun resolveSubSpaceAtEntity(world: Level, x: Int, z: Int): Triple<Region, GeoScope, SubSpace?>? =
        RegionDatabase.getRegionScopeSubSpaceAt(world, x, z)

    fun resolveSubSpaceAtEntity(world: Level, blockPos: BlockPos): Triple<Region, GeoScope, SubSpace?>? =
        RegionDatabase.getRegionScopeSubSpaceAt(world, blockPos.x, blockPos.z)

    fun getEffectiveEffectsForScope(region: Region, scope: GeoScope): Map<EffectKey, Int> {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        val keys = mutableSetOf<EffectKey>()
        keys.addAll(scope.settingStore.effectKeys())
        keys.addAll(region.settingStore.effectKeys())
        val overlay = scope.assignedScopeIdOrNull
            ?.let(com.imyvm.iwg.application.region.effect.EffectOverlayService::queryOverlay)
            ?: emptyMap()
        keys.addAll(overlay.keys)

        val result = mutableMapOf<EffectKey, Int>()
        for (key in keys) {
            val overlayValue = overlay[key]
            if (overlayValue != null) {
                result[key] = overlayValue
                continue
            }
            val scopeValue = scope.settingStore.globalEffect(key)
            if (scopeValue != null) {
                result[key] = scopeValue
                continue
            }
            val regionValue = region.settingStore.globalEffect(key)
            if (regionValue != null) result[key] = regionValue
        }
        return result
    }

    fun getEffectiveRulesForScope(region: Region, scope: GeoScope): Map<RuleKey, Boolean> {
        val keys = mutableSetOf<RuleKey>()
        keys.addAll(scope.settingStore.builtInRuleKeys())
        keys.addAll(region.settingStore.builtInRuleKeys())
        val result = mutableMapOf<RuleKey, Boolean>()
        for (key in keys) {
            result[key] = getEffectiveScopeRuleValue(region, scope, key)
        }
        return result
    }

    fun applyTimedEffectOverlay(overlay: com.imyvm.iwg.domain.TimedEffectOverlay): String =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.applyTimedEffectOverlay(overlay)

    /** Creates a validated overlay with an immutable snapshot of [effects]. */
    fun createTimedEffectOverlay(
        overlayId: String,
        scopeId: AssignedScopeId,
        effects: List<TimedEffect>,
        startMillis: Long,
        endMillis: Long,
        priority: Int,
        source: String
    ): TimedEffectOverlay = TimedEffectOverlay(
        overlayId, scopeId.raw, effects, startMillis, endMillis, priority, source
    ).immutableSnapshot()

    fun clearTimedEffectOverlay(scopeId: AssignedScopeId, overlayId: String): Boolean =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.clearTimedEffectOverlay(scopeId, overlayId)

    @Deprecated("Use the AssignedScopeId overload")
    fun clearTimedEffectOverlay(scopeId: ScopeId, overlayId: String): Boolean =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.clearTimedEffectOverlay(scopeId, overlayId)

    fun queryOverlay(scopeId: AssignedScopeId): Map<EffectKey, Int> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryOverlay(scopeId)

    @Deprecated("Use the AssignedScopeId overload")
    fun queryOverlay(scopeId: ScopeId): Map<EffectKey, Int> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryOverlay(scopeId)

    fun queryActiveOverlays(scopeId: AssignedScopeId): List<com.imyvm.iwg.domain.TimedEffectOverlay> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryActiveOverlays(scopeId)

    @Deprecated("Use the AssignedScopeId overload")
    fun queryActiveOverlays(scopeId: ScopeId): List<com.imyvm.iwg.domain.TimedEffectOverlay> =
        com.imyvm.iwg.application.region.effect.EffectOverlayService.queryActiveOverlays(scopeId)
}
