package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun validatePolygon(player: ServerPlayer, existingScope: GeoScope): Boolean {
    val geometry = existingScope.geoShape?.typedGeometry as? PolygonGeometry
    if (geometry == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.invalid_polygon")!!)
        return false
    }
    return true
}

fun getPolygonPoints(existingScope: GeoScope): List<BlockPos> {
    val geometry = existingScope.geoShape?.typedGeometry as PolygonGeometry
    return List(geometry.vertexCount) { i -> BlockPos(geometry.x(i), 0, geometry.z(i)) }
}