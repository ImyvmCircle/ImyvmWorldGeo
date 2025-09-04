package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.*
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.inter.commands.errorMessage
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

object ImyvmWorldGeoApi {
    fun startSelection(player: ServerPlayerEntity): Int{
        return startSelection(player)
    }

    fun stopSelection(player: ServerPlayerEntity): Int{
        return stopSelection(player)
    }

    fun resetSelection(player: ServerPlayerEntity): Int{
        return resetSelection(player)
    }

    fun createRegion(
        player: ServerPlayerEntity,
        name: String?,
        shapeTypeName: String?): Int{
        if (!selectionModeCheck(player)) return 0
        val regionName = getNameCheck(player, name) ?: return 0
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
            else -> {
                player.sendMessage(Translator.tr("error.unknown"))
                0
            }
        }
    }
}