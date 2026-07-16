package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/** Legacy JVM compatibility helper. New production code reads typed geometry directly. */
@Deprecated("Read PolygonGeometry from GeoScope.geoShape")
fun validatePolygon(player: ServerPlayer, existingScope: GeoScope): Boolean {
    val geometry = existingScope.geoShape?.typedGeometry as? PolygonGeometry
    if (geometry == null) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.scope.modify.invalid_polygon")))
        return false
    }
    return true
}

/** Legacy JVM compatibility helper. The returned positions are detached snapshots. */
@Deprecated("Read PolygonGeometry coordinates directly")
fun getPolygonPoints(existingScope: GeoScope): List<BlockPos> {
    val geometry = existingScope.geoShape?.typedGeometry as? PolygonGeometry
        ?: throw IllegalArgumentException("scope must contain polygon geometry")
    return List(geometry.vertexCount) { index -> BlockPos(geometry.x(index), 0, geometry.z(index)) }
}
