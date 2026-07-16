package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet

object DynmapRegionRenderer {

    fun renderScope(markerSet: MarkerSet, markerAPI: MarkerAPI, region: Region, scope: GeoScope, color: Int) {
        val shape = scope.geoShape ?: return
        val worldName = worldName(scope.worldId)
        val label = "${region.name}:${scope.scopeName}"
        val id = dynmapScopeMarkerId(scope)
        when (val geometry = shape.typedGeometry) {
            is CircleGeometry -> renderCircle(markerSet, id, label, worldName, geometry, color)
            is RectangleGeometry -> renderRectangle(markerSet, id, label, worldName, geometry, color)
            is PolygonGeometry -> renderPolygon(markerSet, id, label, worldName, geometry, color)
            UnknownGeometry -> return
        }
        scope.teleportPoint?.let {
            renderTeleportPoint(markerSet, markerAPI, region, scope, it, worldName)
        }
    }

    private fun renderCircle(markerSet: MarkerSet, id: String, label: String, world: String, geometry: CircleGeometry, color: Int) {
        val cx = geometry.centerX.toDouble()
        val cz = geometry.centerZ.toDouble()
        val r = geometry.radius.toDouble()
        markerSet.createCircleMarker(id, label, false, world, cx, 64.0, cz, r, r, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderRectangle(markerSet: MarkerSet, id: String, label: String, world: String, geometry: RectangleGeometry, color: Int) {
        val xs = doubleArrayOf(geometry.west.toDouble(), geometry.east.toDouble(), geometry.east.toDouble(), geometry.west.toDouble())
        val zs = doubleArrayOf(geometry.north.toDouble(), geometry.north.toDouble(), geometry.south.toDouble(), geometry.south.toDouble())
        markerSet.createAreaMarker(id, label, false, world, xs, zs, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderPolygon(markerSet: MarkerSet, id: String, label: String, world: String, geometry: PolygonGeometry, color: Int) {
        val count = geometry.vertexCount
        val xs = DoubleArray(count) { geometry.x(it).toDouble() }
        val zs = DoubleArray(count) { geometry.z(it).toDouble() }
        markerSet.createAreaMarker(id, label, false, world, xs, zs, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderTeleportPoint(markerSet: MarkerSet, markerAPI: MarkerAPI, region: Region, scope: GeoScope, tp: BlockPos, world: String) {
        val tpId = dynmapTeleportMarkerId(scope)
        val tpLabel = "${region.name}:${scope.scopeName}"
        val icon = markerAPI.getMarkerIcon("house") ?: markerAPI.getMarkerIcon("default") ?: return
        markerSet.createMarker(tpId, tpLabel, false, world, tp.x.toDouble(), tp.y.toDouble(), tp.z.toDouble(), icon, false)
    }

    private fun worldName(worldId: Identifier): String = when (worldId.toString()) {
        "minecraft:overworld" -> "world"
        "minecraft:the_nether" -> "world_nether"
        "minecraft:the_end" -> "world_the_end"
        else -> worldId.path
    }
}

internal fun dynmapScopeMarkerId(scope: GeoScope): String =
    "iwg_${scope.requireAssignedScopeId().toIdString()}"

internal fun dynmapTeleportMarkerId(scope: GeoScope): String =
    "iwgtp_${scope.requireAssignedScopeId().toIdString()}"
