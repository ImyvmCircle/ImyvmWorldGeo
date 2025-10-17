package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun recreateScope(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    newPositions: MutableList<BlockPos>,
    shapeType: Region.Companion.GeoShapeType,
    successMessageKey: String,
    vararg extraArgs: Any
) {
    region.geometryScope.remove(existingScope)
    val newScope = RegionFactory.createScope(scopeName = existingScope.scopeName, selectedPositions = newPositions, shapeType = shapeType)
    when (newScope) {
        is Result.Ok -> {
            region.geometryScope.add(newScope.value)
            player.sendMessage(Translator.tr(successMessageKey, existingScope.scopeName, region.name, *extraArgs))
            ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
        }
        is Result.Err -> {
            region.geometryScope.add(existingScope)
            val errorMsg = errorMessage(newScope.error, shapeType)
            player.sendMessage(errorMsg)
        }
    }
}