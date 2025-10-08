package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.comapp.*
import com.imyvm.iwg.domain.Region
import net.minecraft.server.network.ServerPlayerEntity

@Suppress("unused")
object PlayerInteractionApi {
    fun startSelection(player: ServerPlayerEntity) = onStartSelection(player)
    fun stopSelection(player: ServerPlayerEntity) = onStopSelection(player)
    fun resetSelection(player: ServerPlayerEntity) = onResetSelection(player)
    fun createRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?) = onRegionCreation(player, name, shapeTypeName ?: "", isApi = true)
    fun createAndGetRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?) = onTryingRegionCreationWithReturn(player, name, shapeTypeName ?: "", isApi = true)
    fun deleteRegion(player: ServerPlayerEntity, region: Region) = onRegionDelete(player, region)
    fun renameRegion(player: ServerPlayerEntity, region: Region, newName: String) = onRegionRename(player, region, newName)
    fun addScope(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?) = onScopeCreation(player, region, name, shapeTypeName ?: "", isApi = true)
    fun createAndGetRegionScopePair(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?) = onTryingScopeCreationWithReturn(player, region, name, shapeTypeName ?: "", isApi = true)
    fun deleteScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onScopeDelete(player, region, scopeName)
    fun renameScope(player: ServerPlayerEntity, region: Region, oldName: String, newName: String) = onScopeRename(player, region, oldName, newName)
    fun modifyScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)
    fun addSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, valueString, targetPlayerStr)
    fun addSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, targetPlayerStr)
    fun removeSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, null, targetPlayerStr)
    fun removeSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, null, targetPlayerStr)
    fun queryRegionInfo(player: ServerPlayerEntity, region: Region) = onQueryRegion(player, region, true)
}