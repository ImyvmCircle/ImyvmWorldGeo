package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.interaction.saveRegionData
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.Region
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun recreateScope(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    newPositions: List<BlockPos>,
    shapeType: GeoShapeType
): Boolean {
    require(region.containsScope(existingScope)) { "scope does not belong to region" }

    val newShape = RegionFactory.recreateScopeShape(
        region = region,
        existingScope = existingScope,
        selectedPositions = newPositions,
        shapeType = shapeType
    )

    return when (newShape) {
        is Result.Ok -> {
            val oldShape = existingScope.geoShape
            existingScope.replaceGeometry(newShape.value)
            if (!saveRegionData(player)) {
                existingScope.replaceGeometry(oldShape)
                false
            } else {
                clearSelectionDisplay(player)
                ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
                true
            }
        }
        is Result.Err -> {
            val errorMsg = errorMessage(newShape.error, shapeType)
            errorMsg.forEach { player.sendSystemMessage(it) }
            false
        }
    }
}
