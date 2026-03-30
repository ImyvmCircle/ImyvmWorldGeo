package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun recreateScope(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    newPositions: MutableList<BlockPos>,
    shapeType: GeoShapeType,
    successMessageKey: String,
    vararg extraArgs: Any
) {
    val existingWorld = existingScope.worldId
    val existingTeleportPoint = existingScope.teleportPoint
    region.geometryScope.remove(existingScope)

    val newScope = RegionFactory.createScope(
        scopeName = existingScope.scopeName,
        existingWorld = existingWorld,
        existingTeleportPoint = existingTeleportPoint,
        selectedPositions = newPositions,
        shapeType = shapeType
    )

    when (newScope) {
        is Result.Ok -> {
            region.geometryScope.add(newScope.value)
            player.sendSystemMessage(Translator.tr(successMessageKey, existingScope.scopeName, region.name, *extraArgs)!!)
            clearSelectionDisplay(player)
            ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
        }
        is Result.Err -> {
            region.geometryScope.add(existingScope)
            val errorMsg = errorMessage(newScope.error, shapeType)
            errorMsg.forEach { player.sendSystemMessage(it) }
        }
    }
}