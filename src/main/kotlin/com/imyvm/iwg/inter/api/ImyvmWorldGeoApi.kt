package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.comapp.*
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.util.ui.errorMessage
import net.minecraft.server.network.ServerPlayerEntity

@Suppress("unused")
object ImyvmWorldGeoApi {
    fun startSelection(player: ServerPlayerEntity) = onStartSelection(player)
    fun stopSelection(player: ServerPlayerEntity) = onStopSelection(player)
    fun resetSelection(player: ServerPlayerEntity) = onResetSelection(player)
    fun createRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?) = onRegionCreation(player, name, shapeTypeName ?: "", isApi = true)
    fun deleteRegion(player: ServerPlayerEntity, region: Region) = onRegionDelete(player, region)
    fun renameRegion(player: ServerPlayerEntity, region: Region, newName: String) = onRegionRename(player, region, newName)

    fun queryRegionInfo(player: ServerPlayerEntity, region: Region) = onQueryRegion(player, region, true)
}