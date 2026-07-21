package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.component.GeoScope
import org.dynmap.markers.AreaMarker
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet

object DynmapRegionRenderer {

    internal fun upsertMarker(markerSet: MarkerSet, markerAPI: MarkerAPI, spec: DynmapMarkerSpec) {
        when (spec) {
            is DynmapAreaMarkerSpec -> upsertArea(markerSet, spec)
            is DynmapCircleMarkerSpec -> upsertCircle(markerSet, spec)
            is DynmapPointMarkerSpec -> upsertPoint(markerSet, markerAPI, spec)
        }
    }

    private fun upsertArea(markerSet: MarkerSet, spec: DynmapAreaMarkerSpec) {
        val existing = markerSet.findAreaMarker(spec.identity.id)
        if (existing == null) {
            configureArea(createArea(markerSet, spec.identity.id, spec), spec)
            return
        }
        if (existing.world == spec.world) {
            configureArea(existing, spec)
            return
        }

        replaceAreaAcrossWorld(markerSet, existing, spec)
    }

    private fun replaceAreaAcrossWorld(markerSet: MarkerSet, existing: AreaMarker, spec: DynmapAreaMarkerSpec) {
        val stagingId = "${spec.identity.id}__iwg_staging"
        var staging = markerSet.findAreaMarker(stagingId)
        if (staging != null && staging.world != spec.world) {
            staging.deleteMarker()
            staging = null
        }
        val replacement = staging ?: createArea(markerSet, stagingId, spec)
        configureArea(replacement, spec)
        existing.deleteMarker()

        val canonical = createArea(markerSet, spec.identity.id, spec)
        configureArea(canonical, spec)
        replacement.deleteMarker()
    }

    private fun createArea(markerSet: MarkerSet, id: String, spec: DynmapAreaMarkerSpec): AreaMarker =
        markerSet.createAreaMarker(id, spec.label, false, spec.world, spec.x, spec.z, false)
            ?: error("Dynmap refused to create area marker $id")

    private fun configureArea(marker: AreaMarker, spec: DynmapAreaMarkerSpec) {
        marker.setLabel(spec.label)
        marker.setCornerLocations(spec.x, spec.z)
        marker.setFillStyle(0.3, spec.color)
        marker.setLineStyle(2, 0.8, spec.color)
    }

    private fun upsertCircle(markerSet: MarkerSet, spec: DynmapCircleMarkerSpec) {
        val marker = markerSet.findCircleMarker(spec.identity.id)
            ?: markerSet.createCircleMarker(
                spec.identity.id,
                spec.label,
                false,
                spec.world,
                spec.centerX,
                64.0,
                spec.centerZ,
                spec.radius,
                spec.radius,
                false
            )
            ?: error("Dynmap refused to create circle marker ${spec.identity.id}")
        marker.setLabel(spec.label)
        marker.setCenter(spec.world, spec.centerX, 64.0, spec.centerZ)
        marker.setRadius(spec.radius, spec.radius)
        marker.setFillStyle(0.3, spec.color)
        marker.setLineStyle(2, 0.8, spec.color)
    }

    private fun upsertPoint(markerSet: MarkerSet, markerAPI: MarkerAPI, spec: DynmapPointMarkerSpec) {
        val icon = markerAPI.getMarkerIcon("house") ?: markerAPI.getMarkerIcon("default")
            ?: error("Dynmap has no house or default marker icon")
        val marker = markerSet.findMarker(spec.identity.id)
            ?: markerSet.createMarker(
                spec.identity.id,
                spec.label,
                false,
                spec.world,
                spec.x,
                spec.y,
                spec.z,
                icon,
                false
            )
            ?: error("Dynmap refused to create point marker ${spec.identity.id}")
        marker.setLabel(spec.label)
        marker.setLocation(spec.world, spec.x, spec.y, spec.z)
        check(marker.setMarkerIcon(icon)) { "Dynmap refused to set icon for point marker ${spec.identity.id}" }
    }
}

internal fun dynmapScopeMarkerId(scope: GeoScope): String =
    "iwg_${scope.requireAssignedScopeId().toIdString()}"

internal fun dynmapTeleportMarkerId(scope: GeoScope): String =
    "iwgtp_${scope.requireAssignedScopeId().toIdString()}"
