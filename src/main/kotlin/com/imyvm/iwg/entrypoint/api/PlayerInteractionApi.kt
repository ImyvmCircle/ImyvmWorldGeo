package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

@Suppress("unused")
object PlayerInteractionApi {
    fun startSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onStartSelection(player, shapeType)
    fun stopSelection(player: ServerPlayer) = onStopSelection(player)
    fun resetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onResetSelection(player, shapeType)
    fun setSelectionShape(player: ServerPlayer, shapeType: GeoShapeType) = onSetSelectionShape(player, shapeType)
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
    fun teleportPlayerToScope(player: ServerPlayer, targetRegion: Region, scope: GeoScope) = onTeleportingPlayer(player, targetRegion, scope)
    fun toggleTeleportPointAccessibility(scope: GeoScope) = onTogglingTeleportPointAccessibility(scope)
    fun modifyScope(player: ServerPlayer, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)
    fun addSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, valueString, targetPlayerStr)
    fun addSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, targetPlayerStr)
    fun removeSettingRegion(player: ServerPlayer, region: Region, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, null, targetPlayerStr)
    fun removeSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, null, targetPlayerStr)
    fun getPermissionValueRegion(player: ServerPlayer, region: Region?, scopeName: String?, targetPlayerNameStr: String?, keyString: String) =
        onCertificatePermissionValue(player, region, scopeName, targetPlayerNameStr, keyString)
    fun getRuleValueRegion(region: Region?, keyString: String) =
        onCertificateRuleValue(region, null, keyString)
    fun getRuleValueScope(region: Region?, scopeName: String, keyString: String) =
        onCertificateRuleValue(region, scopeName, keyString)
    fun queryRegionInfo(player: ServerPlayer, region: Region) = onQueryRegion(player, region, true)
    fun toggleActionBar(player: ServerPlayer) = onToggleActionBar(player)
    fun estimateRegionArea(player: ServerPlayer, shapeTypeName: String, customPositions: List<BlockPos>? = null) = onEstimateRegionArea(player, shapeTypeName, customPositions)
    fun estimateScopeAreaChange(player: ServerPlayer, region: Region, scopeName: String, customPositions: List<BlockPos>? = null) = onEstimateScopeAreaChange(player, region, scopeName, customPositions)

    fun addEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?) = onHandleSetting(player, region, null, keyString, valueString, null)

    fun addEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, null)

    fun removeEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String) = onHandleSetting(player, region, null, keyString, null, null)

    fun removeEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String) = onHandleSetting(player, region, scopeName, keyString, null, null)
}