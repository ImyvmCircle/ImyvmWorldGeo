package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import net.minecraft.server.network.ServerPlayerEntity

@Suppress("unused")
object PlayerInteractionApi {
    fun startSelection(player: ServerPlayerEntity) = onStartSelection(player)
    fun stopSelection(player: ServerPlayerEntity) = onStopSelection(player)
    fun resetSelection(player: ServerPlayerEntity) = onResetSelection(player)
    fun createRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?, idMark: Int = 0) = onRegionCreation(player, name, shapeTypeName ?: "", isApi = true, idMark)
    fun createAndGetRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?, idMark: Int = 0) = onTryingRegionCreationWithReturn(player, name, shapeTypeName ?: "", isApi = true, idMark)
    fun deleteRegion(player: ServerPlayerEntity, region: Region) = onRegionDelete(player, region, isApi = true)
    fun renameRegion(player: ServerPlayerEntity, region: Region, newName: String) = onRegionRename(player, region, newName)
    fun addScope(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?) = onScopeCreation(player, region, name, shapeTypeName ?: "", isApi = true)
    fun createAndGetRegionScopePair(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?) = onTryingScopeCreationWithReturn(player, region, name, shapeTypeName ?: "", isApi = true)
    fun deleteScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onScopeDelete(player, region, scopeName)
    fun renameScope(player: ServerPlayerEntity, region: Region, oldName: String, newName: String) = onScopeRename(player, region, oldName, newName)
    fun addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int) = onAddingTeleportPoint(player, targetRegion, scope, x, y, z)
    fun addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope): Int {
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        return onAddingTeleportPoint(player, targetRegion, scope, x, y, z)
    }
    fun resetTeleportPoint(scope: GeoScope) = onResettingTeleportPoint(scope)
    fun getTeleportPoint(scope: GeoScope) = onGettingTeleportPoint(scope)
    fun modifyScope(player: ServerPlayerEntity, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)
    fun addSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, valueString, targetPlayerStr)
    fun addSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, valueString, targetPlayerStr)
    fun removeSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, null, keyString, null, targetPlayerStr)
    fun removeSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = onHandleSetting(player, region, scopeName, keyString, null, targetPlayerStr)
    fun queryRegionInfo(player: ServerPlayerEntity, region: Region) = onQueryRegion(player, region, true)
}