package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.dynmap.DynmapCommonAPI
import org.dynmap.DynmapCommonAPIListener
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.GenericMarker
import org.dynmap.markers.MarkerSet

object DynmapIntegration : DynmapCommonAPIListener() {

    private const val MARKER_SET_ID = "imyvmworldgeo_regions"
    private const val MARKER_SET_LABEL = "IMYVM Regions"

    private var markerAPI: MarkerAPI? = null
    private var markerSet: MarkerSet? = null

    fun registerIfLoaded() {
        DynmapCommonAPIListener.register(this)
        RegionDatabase.onSave = { syncRegions() }
        ServerLifecycleEvents.SERVER_STARTED.register { syncRegions() }
        ImyvmWorldGeo.logger.info("Dynmap detected, region map integration enabled.")
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
            val desired = buildDynmapProjection(RegionDatabase.getRegionList())
            val existing = snapshotMarkers(set)
            val result = reconcileDynmapProjection(
                desired,
                existing,
                ExistingDynmapMarker::identity,
                { DynmapRegionRenderer.upsertMarker(set, api, it) },
                { it.marker.deleteMarker() }
            )
            result.upsertFailures.forEach { failure ->
                ImyvmWorldGeo.logger.error(
                    "Failed to upsert Dynmap marker ${failure.identity.kind}:${failure.identity.id}: ${failure.cause.message}",
                    failure.cause
                )
            }
            result.deletionFailures.forEach { failure ->
                ImyvmWorldGeo.logger.error(
                    "Failed to delete stale Dynmap marker ${failure.identity.kind}:${failure.identity.id}: ${failure.cause.message}",
                    failure.cause
                )
            }
        } catch (e: Exception) {
            ImyvmWorldGeo.logger.error("Failed to sync regions to Dynmap: ${e.message}", e)
        }
    }

    private fun snapshotMarkers(set: MarkerSet): List<ExistingDynmapMarker> = buildList {
        set.areaMarkers.toList().forEach {
            add(ExistingDynmapMarker(DynmapMarkerIdentity(DynmapMarkerKind.AREA, it.markerID), it))
        }
        set.circleMarkers.toList().forEach {
            add(ExistingDynmapMarker(DynmapMarkerIdentity(DynmapMarkerKind.CIRCLE, it.markerID), it))
        }
        set.markers.toList().forEach {
            add(ExistingDynmapMarker(DynmapMarkerIdentity(DynmapMarkerKind.POINT, it.markerID), it))
        }
    }

    private data class ExistingDynmapMarker(
        val identity: DynmapMarkerIdentity,
        val marker: GenericMarker
    )
}
