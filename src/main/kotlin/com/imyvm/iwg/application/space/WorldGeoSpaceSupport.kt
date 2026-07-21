package com.imyvm.iwg.application.space

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoSettingSummary
import com.imyvm.iwg.domain.WorldGeoSettingVisibility
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import com.imyvm.iwg.domain.WorldGeoSpaceType
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.ExtensionRuleSetting
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.Setting
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.util.Collections

private const val SPACE_STATS_VERSION = 1L

object WorldGeoSpaceSupport {
    fun snapshot(region: Region): WorldGeoSpaceSnapshot {
        return WorldGeoSpaceSnapshot(
            type = WorldGeoSpaceType.REGION,
            id = region.numberID.toLong(),
            name = region.name,
            dimensionId = null,
            area = region.calculateTotalArea(),
            parentRegionId = null,
            parentRegionName = null,
            parentScopeId = null,
            parentScopeName = null,
            childScopeCount = region.scopes.size,
            childSubSpaceCount = region.subSpaces.size,
            stringTags = emptySet(),
            keyedTags = emptyMap(),
            statsVersion = SPACE_STATS_VERSION
        )
    }

    fun snapshot(region: Region, scope: GeoScope): WorldGeoSpaceSnapshot {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        return WorldGeoSpaceSnapshot(
            type = WorldGeoSpaceType.GEOSCOPE,
            id = scope.requireAssignedScopeId().raw,
            name = scope.scopeName,
            dimensionId = scope.worldId,
            area = scope.geoShape?.calculateArea(),
            parentRegionId = region.numberID,
            parentRegionName = region.name,
            parentScopeId = null,
            parentScopeName = null,
            childScopeCount = 0,
            childSubSpaceCount = region.subSpaces.count { it.parentScopeId == scope.requireAssignedScopeId() },
            stringTags = emptySet(),
            keyedTags = emptyMap(),
            statsVersion = SPACE_STATS_VERSION
        )
    }

    fun snapshot(region: Region, scope: GeoScope, subSpace: SubSpace): WorldGeoSpaceSnapshot {
        require(region.containsScope(scope)) { "scope does not belong to region" }
        require(region.containsSubSpace(subSpace)) { "subspace does not belong to region" }
        require(subSpace.parentScopeId == scope.requireAssignedScopeId()) { "subspace parent scope does not match" }
        return WorldGeoSpaceSnapshot(
            type = WorldGeoSpaceType.SUBSPACE,
            id = subSpace.subSpaceId,
            name = subSpace.name,
            dimensionId = subSpace.worldId,
            area = subSpace.geoShape.calculateArea(),
            parentRegionId = region.numberID,
            parentRegionName = region.name,
            parentScopeId = scope.requireAssignedScopeId().raw,
            parentScopeName = scope.scopeName,
            childScopeCount = 0,
            childSubSpaceCount = 0,
            stringTags = Collections.unmodifiableSet(linkedSetOf<String>().also { it.addAll(subSpace.stringTags) }),
            keyedTags = Collections.unmodifiableMap(linkedMapOf<String, String>().also { it.putAll(subSpace.keyedTags) }),
            statsVersion = SPACE_STATS_VERSION
        )
    }

    fun settingSummaries(region: Region, visibility: WorldGeoSettingVisibility): List<WorldGeoSettingSummary> {
        return settingSummaries(region.settings, WorldGeoSpaceType.REGION, region.numberID.toLong(), visibility)
    }

    fun settingSummaries(scope: GeoScope, visibility: WorldGeoSettingVisibility): List<WorldGeoSettingSummary> {
        return settingSummaries(scope.settings, WorldGeoSpaceType.GEOSCOPE, scope.requireAssignedScopeId().raw, visibility)
    }

    fun settingSummaries(subSpace: SubSpace, visibility: WorldGeoSettingVisibility): List<WorldGeoSettingSummary> {
        return settingSummaries(subSpace.settings, WorldGeoSpaceType.SUBSPACE, subSpace.subSpaceId, visibility)
    }

    fun sendMessage(server: MinecraftServer, region: Region, message: Component): Int {
        RegionDatabase.requireCanonicalRegion(region)
        return send(server, message) { resolved -> resolved.first.numberID == region.numberID }
    }

    fun sendMessage(server: MinecraftServer, region: Region, scope: GeoScope, message: Component): Int {
        RegionDatabase.requireCanonicalScope(region, scope)
        val scopeId = scope.requireAssignedScopeId()
        return send(server, message) { resolved ->
            resolved.first.numberID == region.numberID && resolved.second.requireAssignedScopeId() == scopeId
        }
    }

    fun sendMessage(server: MinecraftServer, region: Region, scope: GeoScope, subSpace: SubSpace, message: Component): Int {
        RegionDatabase.requireCanonicalSubSpace(region, scope, subSpace)
        return send(server, message) { resolved ->
            resolved.first.numberID == region.numberID && resolved.third?.subSpaceId == subSpace.subSpaceId
        }
    }

    private fun send(
        server: MinecraftServer,
        message: Component,
        matches: (Triple<Region, GeoScope, SubSpace?>) -> Boolean
    ): Int {
        var count = 0
        getOnlinePlayers(server).forEach { player ->
            val pos = player.blockPosition()
            val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), pos.x, pos.z)
            if (resolved != null && matches(resolved)) {
                player.sendSystemMessage(message)
                count++
            }
        }
        return count
    }

    private fun settingSummaries(
        settings: List<Setting>,
        spaceType: WorldGeoSpaceType,
        spaceId: Long,
        visibility: WorldGeoSettingVisibility
    ): List<WorldGeoSettingSummary> = settings
        .asSequence()
        .filter { visibility == WorldGeoSettingVisibility.OP_DEBUG || !it.isPersonal }
        .map { setting ->
            WorldGeoSettingSummary(
                spaceType = spaceType,
                spaceId = spaceId,
                key = setting.key.toString(),
                value = setting.value.toString(),
                settingType = settingType(setting),
                subject = setting.playerUUID?.toString() ?: "GLOBAL"
            )
        }
        .toList()

    private fun settingType(setting: Setting): String = when (setting) {
        is PermissionSetting, is ExtensionPermissionSetting -> "PERMISSION"
        is RuleSetting, is ExtensionRuleSetting -> "RULE"
        is EffectSetting -> "EFFECT"
        is EntryExitToggleSetting, is EntryExitMessageSetting -> "ENTRY_EXIT"
        else -> setting.javaClass.simpleName
    }
}
