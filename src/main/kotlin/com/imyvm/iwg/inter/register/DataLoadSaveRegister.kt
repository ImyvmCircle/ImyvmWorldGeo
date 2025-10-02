package com.imyvm.iwg.inter.register

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

fun registerDataLoadSave(){
    dataLoad()
    dataSave()
}

private fun dataLoad() {
    try {
        RegionDatabase.load()
    } catch (e: Exception) {
        ImyvmWorldGeo.logger.error("Failed to load region database: ${e.message}", e)
    }
}

private fun dataSave() {
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        try {
            RegionDatabase.save()
        } catch (e: Exception) {
            ImyvmWorldGeo.logger.error("Failed to save region database: ${e.message}", e)
        }
    }
}