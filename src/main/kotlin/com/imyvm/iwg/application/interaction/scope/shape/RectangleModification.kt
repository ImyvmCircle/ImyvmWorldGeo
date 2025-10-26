package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun modifyScopeRectangle(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (shapeParams.isNullOrEmpty() || shapeParams.size < 4) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.rectangle.invalid_rectangle"))
        return
    }

    val point = selectedPositions[0]
    val (west, north, east, south) = updateRectangleBounds(point, shapeParams)

    val newPositions = mutableListOf(
        BlockPos(west, 0, north),
        BlockPos(east, 0, south)
    )

    recreateScope(
        player, region, existingScope, newPositions,
        GeoShapeType.RECTANGLE,
        "interaction.meta.scope.modify.rectangle.success",
        west, north, east, south
    )
}

private fun updateRectangleBounds(point: BlockPos, shapeParams: List<Int>): List<Int> {
    var west = shapeParams[0]
    var north = shapeParams[1]
    var east = shapeParams[2]
    var south = shapeParams[3]

    if (kotlin.math.abs(point.x - west) < kotlin.math.abs(point.x - east)) {
        west = point.x
    } else {
        east = point.x
    }

    if (kotlin.math.abs(point.z - north) < kotlin.math.abs(point.z - south)) {
        north = point.z
    } else {
        south = point.z
    }

    return listOf(west, north, east, south)
}