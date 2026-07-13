package com.imyvm.iwg.entrypoint.register

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

fun registerDataLoadSave(){
    dataLoad()
    dataSave()
}

private fun dataLoad() {
    RegionDatabase.load()
}

private fun dataSave() {
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        try {
            PlayerRegionEntryExitTracker.flushAllDurations()
            RegionDatabase.save()
        } catch (e: Exception) {
            ImyvmWorldGeo.logger.error("Failed to save region database: ${e.message}", e)
        }
    }
}
