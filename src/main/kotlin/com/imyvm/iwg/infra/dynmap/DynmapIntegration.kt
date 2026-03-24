package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.loader.api.FabricLoader
import org.dynmap.DynmapCommonAPI
import org.dynmap.DynmapCommonAPIListener
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet

object DynmapIntegration : DynmapCommonAPIListener() {

    private const val MARKER_SET_ID = "imyvmworldgeo_regions"
    private const val MARKER_SET_LABEL = "IMYVM Regions"

    private var markerAPI: MarkerAPI? = null
    private var markerSet: MarkerSet? = null

    fun registerIfLoaded() {
        if (FabricLoader.getInstance().isModLoaded("dynmap")) {
            RegionDatabase.onSave = { syncRegions() }
            DynmapCommonAPIListener.register(this)
            ImyvmWorldGeo.logger.info("Dynmap detected, region map integration enabled.")
        }
    }

    override fun apiEnabled(api: DynmapCommonAPI) {
        markerAPI = api.markerAPI
        markerSet = markerAPI?.getMarkerSet(MARKER_SET_ID)
            ?: markerAPI?.createMarkerSet(MARKER_SET_ID, MARKER_SET_LABEL, null, false)
        syncRegions()
    }

    override fun apiDisabled(api: DynmapCommonAPI) {
        markerAPI = null
        markerSet = null
    }

    fun syncRegions() {
        val set = markerSet ?: return
        val api = markerAPI ?: return
        try {
            set.areaMarkers.toList().forEach { it.deleteMarker() }
            set.circleMarkers.toList().forEach { it.deleteMarker() }
            set.markers.toList().forEach { it.deleteMarker() }
            for (region in RegionDatabase.getRegionList()) {
                if (!region.showOnDynmap) continue
                val color = DynmapColorResolver.resolveColor(region)
                for (scope in region.geometryScope) {
                    if (!scope.showOnDynmap) continue
                    DynmapRegionRenderer.renderScope(set, api, region, scope, color)
                }
            }
        } catch (e: Exception) {
            ImyvmWorldGeo.logger.error("Failed to sync regions to Dynmap: ${e.message}", e)
        }
    }
}
