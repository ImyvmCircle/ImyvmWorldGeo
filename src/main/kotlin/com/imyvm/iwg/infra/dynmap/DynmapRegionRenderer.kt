package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet

object DynmapRegionRenderer {

    fun renderScope(markerSet: MarkerSet, markerAPI: MarkerAPI, region: Region, scope: GeoScope, color: Int) {
        val shape = scope.geoShape ?: return
        val worldName = worldName(scope.worldId)
        val label = "${region.name}:${scope.scopeName}"
        val id = markerId(region, scope)
        when (shape.geoShapeType) {
            GeoShapeType.CIRCLE -> renderCircle(markerSet, id, label, worldName, shape, color)
            GeoShapeType.RECTANGLE -> renderRectangle(markerSet, id, label, worldName, shape, color)
            GeoShapeType.POLYGON -> renderPolygon(markerSet, id, label, worldName, shape, color)
            else -> return
        }
        scope.teleportPoint?.let {
            renderTeleportPoint(markerSet, markerAPI, region, scope, it, worldName)
        }
    }

    private fun renderCircle(markerSet: MarkerSet, id: String, label: String, world: String, shape: GeoShape, color: Int) {
        if (shape.shapeParameter.size < 3) return
        val cx = shape.shapeParameter[0].toDouble()
        val cz = shape.shapeParameter[1].toDouble()
        val r = shape.shapeParameter[2].toDouble()
        markerSet.createCircleMarker(id, label, false, world, cx, 64.0, cz, r, r, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderRectangle(markerSet: MarkerSet, id: String, label: String, world: String, shape: GeoShape, color: Int) {
        if (shape.shapeParameter.size < 4) return
        val west = shape.shapeParameter[0].toDouble()
        val north = shape.shapeParameter[1].toDouble()
        val east = shape.shapeParameter[2].toDouble()
        val south = shape.shapeParameter[3].toDouble()
        val xs = doubleArrayOf(west, east, east, west)
        val zs = doubleArrayOf(north, north, south, south)
        markerSet.createAreaMarker(id, label, false, world, xs, zs, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderPolygon(markerSet: MarkerSet, id: String, label: String, world: String, shape: GeoShape, color: Int) {
        if (shape.shapeParameter.size < 6 || shape.shapeParameter.size % 2 != 0) return
        val pairs = shape.shapeParameter.chunked(2)
        val xs = pairs.map { it[0].toDouble() }.toDoubleArray()
        val zs = pairs.map { it[1].toDouble() }.toDoubleArray()
        markerSet.createAreaMarker(id, label, false, world, xs, zs, false)?.apply {
            setFillStyle(0.3, color)
            setLineStyle(2, 0.8, color)
        }
    }

    private fun renderTeleportPoint(markerSet: MarkerSet, markerAPI: MarkerAPI, region: Region, scope: GeoScope, tp: BlockPos, world: String) {
        val tpId = teleportMarkerId(region, scope)
        val tpLabel = "${region.name}:${scope.scopeName}"
        val icon = markerAPI.getMarkerIcon("house") ?: markerAPI.getMarkerIcon("default") ?: return
        markerSet.createMarker(tpId, tpLabel, false, world, tp.x.toDouble(), tp.y.toDouble(), tp.z.toDouble(), icon, false)
    }

    private fun markerId(region: Region, scope: GeoScope): String =
        "iwg_${region.numberID}_${sanitize(scope.scopeName)}"

    private fun teleportMarkerId(region: Region, scope: GeoScope): String =
        "iwgtp_${region.numberID}_${sanitize(scope.scopeName)}"

    private fun sanitize(str: String): String =
        str.replace(Regex("[^a-zA-Z0-9_]"), "_")

    private fun worldName(worldId: Identifier): String = when (worldId.toString()) {
        "minecraft:overworld" -> "world"
        "minecraft:the_nether" -> "world_nether"
        "minecraft:the_end" -> "world_the_end"
        else -> worldId.path
    }
}