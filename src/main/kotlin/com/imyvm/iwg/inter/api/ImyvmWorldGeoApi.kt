package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.comapp.*
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.inter.register.errorMessage
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

@Suppress("unused")
object ImyvmWorldGeoApi {

    @Suppress("MemberVisibilityCanBePrivate")
    fun startSelection(player: ServerPlayerEntity): Int{
        return startSelection(player)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun stopSelection(player: ServerPlayerEntity): Int{
        return stopSelection(player)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun resetSelection(player: ServerPlayerEntity): Int{
        return resetSelection(player)
    }

    fun createRegion(
        player: ServerPlayerEntity,
        name: String?,
        shapeTypeName: String?): Int{
        if (!selectionModeCheck(player)) return 0
        val regionName = getRegionNameCheck(player, name) ?: return 0
        val shapeType = getShapeTypeCheck(player, shapeTypeName ?: "") ?: return 0

        return when (val creationResult = tryRegionCreation(player, regionName, shapeType)) {
            is Result.Ok -> {
                handleRegionCreateSuccess(player, creationResult)
                1
            }
            is Result.Err -> {
                val errorMsg = errorMessage(creationResult.error, shapeType)
                player.sendMessage(errorMsg)
                0
            }
        }
    }

    fun queryRegionInfo(player: ServerPlayerEntity, region: Region): Int{
        player.sendMessage(
            Translator.tr(
                "api.query.result",
                region.name,
                region.numberID,
                region.calculateTotalArea().toString()
            )
        )

        val shapeInfos = region.getScopeInfos()
        shapeInfos.forEach { info ->
            player.sendMessage(info)
        }

        return 1
    }
}