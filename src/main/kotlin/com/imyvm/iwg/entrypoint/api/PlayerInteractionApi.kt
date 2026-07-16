package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.application.interaction.scope.onReplacingScopeShape
import com.imyvm.iwg.application.interaction.getDefaultValueForPermission
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeNotFoundException
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

/**
 * Supported player-driven mutation API for addons.
 *
 * Compatibility and deprecation policy: `docs/addon-api-compatibility.md`.
 */
@Suppress("unused")
object PlayerInteractionApi {
    /** Starts a normal creation selection. Call selection operations on the Minecraft server thread. */
    fun startSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onStartSelection(player, shapeType)
    fun stopSelection(player: ServerPlayer) = onStopSelection(player)

    /** Clears points without changing mode. A shape may be supplied only for a creation selection. */
    fun resetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onResetSelection(player, shapeType)
    fun setSelectionShape(player: ServerPlayer, shapeType: GeoShapeType) = onSetSelectionShape(player, shapeType)

    /**
     * Starts a modification selection for an exact live Scope in RegionDatabase.
     *
     * The Scope must be assigned, have a supported geometry, and belong to the player's current
     * dimension. Detached copies, orphaned Scopes, and later attempts to modify another Scope fail.
     */
    fun startSelectionForModify(player: ServerPlayer, scope: GeoScope) = onStartSelectionForModify(player, scope)
    fun createRegion(player: ServerPlayer, name: String?, idMark: Int = 0) = onRegionCreation(player, name, null, isApi = true, idMark)
    fun createAndGetRegion(player: ServerPlayer, name: String?, idMark: Int = 0) = onTryingRegionCreationWithReturn(player, name, null, isApi = true, idMark)
    fun deleteRegion(player: ServerPlayer, region: Region) = onRegionDelete(player, region, isApi = true)
    fun renameRegion(player: ServerPlayer, region: Region, newName: String) = onRegionRename(player, region, newName)
    fun addScope(player: ServerPlayer, region: Region, name: String?) = onScopeCreation(player, region, name, null, isApi = true)
    fun createAndGetRegionScopePair(player: ServerPlayer, region: Region, name: String?) = onTryingScopeCreationWithReturn(player, region, name, null, isApi = true)
    fun deleteScope(player: ServerPlayer, region: Region, scopeName: String) = onScopeDelete(player, region, scopeName)
    fun renameScope(player: ServerPlayer, region: Region, oldName: String, newName: String) = onScopeRename(player, region, oldName, newName)
    fun transferScope(player: ServerPlayer, sourceRegion: Region, scopeName: String, targetRegion: Region) = onScopeTransfer(player, sourceRegion, scopeName, targetRegion)
    fun mergeRegion(player: ServerPlayer, sourceRegion: Region, targetRegion: Region) = onRegionMerge(player, sourceRegion, targetRegion)
    fun addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int) = onAddingTeleportPoint(player, targetRegion, scope, x, y, z)
    fun addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope) =
        onAddingTeleportPoint(player, targetRegion, scope, player.blockPosition().x, player.blockPosition().y, player.blockPosition().z)
    fun resetTeleportPoint(player: ServerPlayer, region: Region, scope: GeoScope) = onResettingTeleportPoint(player, region, scope)
    fun getTeleportPoint(scope: GeoScope) = onGettingTeleportPoint(scope)
    /** Teleports only when the Scope teleport point is publicly accessible. */
    fun teleportPlayerToScope(player: ServerPlayer, targetRegion: Region, scope: GeoScope) = onTeleportingPlayer(player, targetRegion, scope)

    /**
     * Teleports as an administrator, bypassing only teleport-point accessibility.
     * Canonical ownership, dimension availability and physical safety are still enforced.
     */
    fun teleportPlayerToScopeAsAdministrator(player: ServerPlayer, targetRegion: Region, scope: GeoScope) =
        onTeleportingPlayerAsAdministrator(player, targetRegion, scope)
    fun toggleTeleportPointAccessibility(player: ServerPlayer, region: Region, scope: GeoScope) =
        onTogglingTeleportPointAccessibility(player, region, scope)

    /**
     * Compatibility entry point that resolves the Scope's canonical owner from RegionDatabase.
     *
     * Detached copies, orphaned scopes, and unassigned scopes are rejected.
     *
     * @deprecated Since R11 (unreleased). Use [toggleTeleportPointAccessibility] with the
     * executing player and owning Region. Eligible for removal only after two released versions
     * and explicit maintainer approval.
    */
    @Deprecated("Use toggleTeleportPointAccessibility(player, region, scope)")
    @Suppress("DEPRECATION")
    fun toggleTeleportPointAccessibility(scope: GeoScope) = onTogglingTeleportPointAccessibility(scope)
    fun modifyScope(player: ServerPlayer, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)

    /**
     * Replaces the geometry of an exact live Scope with a complete immutable shape of the same type.
     * Size and intersection policy are validated before mutation; persistence failure restores the old shape.
     */
    fun replaceScopeShape(player: ServerPlayer, region: Region, scope: GeoScope, newShape: GeoShape) =
        onReplacingScopeShape(player, region, scope, newShape)
    fun addSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = addRegionSetting(player, region, keyString, valueString, targetPlayerStr)
    fun addSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = addScopeSetting(player, region, region.getScopeByName(scopeName), keyString, valueString, targetPlayerStr)
    fun removeSettingRegion(player: ServerPlayer, region: Region, keyString: String, targetPlayerStr: String?) = removeRegionSetting(player, region, keyString, targetPlayerStr)
    fun removeSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = removeScopeSetting(player, region, region.getScopeByName(scopeName), keyString, targetPlayerStr)
    fun getDefaultPermissionValue(keyString: String): Boolean {
        val key = PermissionKey.entries.firstOrNull { it.name == keyString }
        if (key != null) return getDefaultValueForPermission(key)
        if (ExtensionSettingRegistry.isRegisteredPermissionKey(keyString)) {
            return getDefaultValueForPermission(ExtensionSettingRegistry.permissionKey(keyString))
        }
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    fun getRegionPermissionValue(player: ServerPlayer, region: Region, keyString: String): Boolean =
        onCertificatePermissionValue(player, region, null, null, keyString)
    fun getRegionPlayerPermissionValue(
        player: ServerPlayer,
        region: Region,
        targetPlayerName: String,
        keyString: String
    ): Boolean = onCertificatePermissionValue(player, region, null, targetPlayerName, keyString)
    fun getScopePermissionValue(player: ServerPlayer, region: Region, scope: GeoScope, keyString: String): Boolean =
        onCertificatePermissionValue(player, region, scope, null, keyString)
    fun getScopePlayerPermissionValue(
        player: ServerPlayer,
        region: Region,
        scope: GeoScope,
        targetPlayerName: String,
        keyString: String
    ): Boolean = onCertificatePermissionValue(player, region, scope, targetPlayerName, keyString)

    /**
     * Compatibility dispatcher for the former nullable permission API.
     *
     * @deprecated Since R9 (unreleased). Use the explicit default/Region/Scope and
     * global/player methods. Eligible for removal only after two released versions
     * and explicit maintainer approval.
     */
    @Deprecated("Use an explicit default, region, or scope permission query")
    fun getPermissionValueRegion(player: ServerPlayer, region: Region?, scopeName: String?, targetPlayerNameStr: String?, keyString: String): Boolean {
        if (region == null) {
            require(scopeName == null) { "scope requires region" }
            return getDefaultPermissionValue(keyString)
        }
        val scope = scopeName?.let {
            region.scopes.firstOrNull { s -> s.scopeName.equals(it, ignoreCase = true) }
                ?: throw ScopeNotFoundException(it, region.name)
        }
        return when {
            scope != null && targetPlayerNameStr != null -> getScopePlayerPermissionValue(player, region, scope, targetPlayerNameStr, keyString)
            scope != null -> getScopePermissionValue(player, region, scope, keyString)
            targetPlayerNameStr != null -> getRegionPlayerPermissionValue(player, region, targetPlayerNameStr, keyString)
            else -> getRegionPermissionValue(player, region, keyString)
        }
    }
    @Deprecated("Use RegionDataApi.getRegionRuleValue, getScopeRuleValue, or the extension rule queries")
    fun getRuleValueRegion(region: Region?, keyString: String) =
        onCertificateRuleValue(region, null, keyString)
    @Deprecated("Use RegionDataApi.getScopeRuleValue or getScopeExtensionRuleValue")
    fun getRuleValueScope(region: Region?, scopeName: String, keyString: String): Boolean? {
        val targetRegion = requireNotNull(region) { "scope requires region" }
        val scope = targetRegion.scopes.firstOrNull { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw ScopeNotFoundException(scopeName, targetRegion.name)
        return onCertificateRuleValue(targetRegion, scope, keyString)
    }
    fun queryRegionInfo(player: ServerPlayer, region: Region) = onQueryRegion(player, region, true)
    fun queryRegionNaturalStats(player: ServerPlayer, region: Region, categoryName: String? = null) =
        onQueryRegionNaturalStats(player, region, categoryName, true)
    fun queryRegionPlayerStats(player: ServerPlayer, region: Region) =
        onQueryRegionPlayerStats(player, region, true)
    fun toggleActionBar(player: ServerPlayer) = onToggleActionBar(player)
    fun estimateRegionArea(player: ServerPlayer, shapeTypeName: String, customPositions: List<BlockPos>? = null) = onEstimateRegionArea(player, shapeTypeName, customPositions)
    fun estimateScopeAreaChange(player: ServerPlayer, region: Region, scopeName: String, customPositions: List<BlockPos>? = null) = onEstimateScopeAreaChange(player, region, scopeName, customPositions)

    fun addEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?) = addRegionSetting(player, region, keyString, valueString, null)

    fun addEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?) = addScopeSetting(player, region, region.getScopeByName(scopeName), keyString, valueString, null)

    fun removeEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String) = removeRegionSetting(player, region, keyString, null)

    fun removeEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String) = removeScopeSetting(player, region, region.getScopeByName(scopeName), keyString, null)
}
