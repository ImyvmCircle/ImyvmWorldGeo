package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun validatePolygon(player: ServerPlayerEntity, existingScope: Region.Companion.GeoScope): Boolean {
    val shapeParams = existingScope.geoShape?.shapeParameter
    val pointCount = shapeParams?.size

    if (pointCount == null || pointCount < 6 || pointCount % 2 != 0) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.invalid_polygon"))
        return false
    }
    return true
}

fun getPolygonPoints(existingScope: Region.Companion.GeoScope): List<BlockPos> {
    val shapeParams = existingScope.geoShape?.shapeParameter!!
    val coords = shapeParams.chunked(2)
    return coords.map { pair -> BlockPos(pair[0], 0, pair[1]) }
}