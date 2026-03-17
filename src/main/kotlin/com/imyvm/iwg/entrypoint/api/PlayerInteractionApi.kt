package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object PlayerInteractionApi {
    fun startSelection(player: ServerPlayerEntity, shapeType: GeoShapeType? = null) = onStartSelection(player, shapeType)
    fun stopSelection(player: ServerPlayerEntity) = onStopSelection(player)
    fun resetSelection(player: ServerPlayerEntity, shapeType: GeoShapeType? = null) = onResetSelection(player, shapeType)
    fun setSelectionShape(player: ServerPlayerEntity, shapeType: GeoShapeType) = onSetSelectionShape(player, shapeType)
    fun startSelectionForModify(player: ServerPlayerEntity, scope: GeoScope) = onStartSelectionForModify(player, scope)
    fun createRegion(player: ServerPlayerEntity, name: String?, idMark: Int = 0) = onRegionCreation(player, name, null, isApi = true, idMark)
    fun createAndGetRegion(player: ServerPlayerEntity, name: String?, idMark: Int = 0) = onTryingRegionCreationWithReturn(player, name, null, isApi = true, idMark)
    fun deleteRegion(player: ServerPlayerEntity, region: Region) = onRegionDelete(player, region, isApi = true)
    fun renameRegion(player: ServerPlayerEntity, region: Region, newName: String) = onRegionRename(player, region, newName)
    fun addScope(player: ServerPlayerEntity, region: Region, name: String?) = onScopeCreation(player, region, name, null, isApi = true)
    fun createAndGetRegionScopePair(player: ServerPlayerEntity, region: Region, name: String?) = onTryingScopeCreationWithReturn(player, region, name, null, isApi = true)
    fun deleteScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onScopeDelete(player, region, scopeName)
    fun renameScope(player: ServerPlayerEntity, region: Region, oldName: String, newName: String) = onScopeRename(player, region, oldName, newName)
    fun transferScope(player: ServerPlayerEntity, sourceRegion: Region, scopeName: String, targetRegion: Region) = onScopeTransfer(player, sourceRegion, scopeName, targetRegion)
    fun mergeRegion(player: ServerPlayerEntity, sourceRegion: Region, targetRegion: Region) = onRegionMerge(player, sourceRegion, targetRegion)
    fun addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int) = onAddingTeleportPoint(player, targetRegion, scope, x, y, z)
    fun addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope) =
        onAddingTeleportPoint(player, targetRegion, scope, player.blockPos.x, player.blockPos.y, player.blockPos.z)
    fun resetTeleportPoint(player: ServerPlayerEntity, region: Region, scope: GeoScope) = onResettingTeleportPoint(player, region, scope)
    fun getTeleportPoint(scope: GeoScope) = onGettingTeleportPoint(scope)
    fun teleportPlayerToScope(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope) = onTeleportingPlayer(player, targetRegion, scope)
    fun toggleTeleportPointAccessibility(scope: GeoScope) = onTogglingTeleportPointAccessibility(scope)
    fun modifyScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)
    fun addSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, valueString, targetPlayerStr)
    fun addSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, targetPlayerStr)
    fun removeSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, null, targetPlayerStr)
    fun removeSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, null, targetPlayerStr)
    fun getPermissionValueRegion(player: ServerPlayerEntity, region: Region?, scopeName: String?, targetPlayerNameStr: String?, keyString: String) =
        onCertificatePermissionValue(player, region, scopeName, targetPlayerNameStr, keyString)
    fun getRuleValueRegion(region: Region?, keyString: String) =
        onCertificateRuleValue(region, null, keyString)
    fun getRuleValueScope(region: Region?, scopeName: String, keyString: String) =
        onCertificateRuleValue(region, scopeName, keyString)
    fun queryRegionInfo(player: ServerPlayerEntity, region: Region) = onQueryRegion(player, region, true)
    fun toggleActionBar(player: ServerPlayerEntity) = onToggleActionBar(player)
    fun estimateRegionArea(player: ServerPlayerEntity, shapeTypeName: String, customPositions: List<BlockPos>? = null) = onEstimateRegionArea(player, shapeTypeName, customPositions)
    fun estimateScopeAreaChange(player: ServerPlayerEntity, region: Region, scopeName: String, customPositions: List<BlockPos>? = null) = onEstimateScopeAreaChange(player, region, scopeName, customPositions)

    fun addEntryExitSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?) = onHandleSetting(player, region, null, keyString, valueString, null)

    fun addEntryExitSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, null)

    fun removeEntryExitSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String) = onHandleSetting(player, region, null, keyString, null, null)

    fun removeEntryExitSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String) = onHandleSetting(player, region, scopeName, keyString, null, null)
}