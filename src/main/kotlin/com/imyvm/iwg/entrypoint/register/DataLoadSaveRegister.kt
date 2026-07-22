package com.imyvm.iwg.entrypoint.register

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.application.region.permission.clearFlySessionState
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.PeriodProcessingStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.TestPeriodModeStore
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path

fun registerDataLoadSave(){
    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        openRegionSession(server.getWorldPath(LevelResource.ROOT))
    }
    ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
        saveForShutdown("player region durations") { PlayerRegionEntryExitTracker.flushAllDurations() }
        saveForShutdown("region database") { RegionDatabase.saveForShutdown() }
        saveForShutdown("period processing store") { PeriodProcessingStore.save() }
        saveForShutdown("test period processing store") { TestPeriodModeStore.save() }
        saveForShutdown("behavior stats store") { BehaviorStatsStore.save() }
    }
    ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
        closeRegionSession()
    }
}

private fun saveForShutdown(label: String, save: () -> Unit) {
    runCatching(save)
        .onFailure { ImyvmWorldGeo.logger.error("Failed to save WorldGeo $label: ${it.message}", it) }
}

internal fun openRegionSession(worldRoot: Path) {
    EffectOverlayService.withScopeLifecycle {
        check(!RegionDatabase.hasActiveSession()) { "Region database session is already active" }
        EffectOverlayService.clearAll()
        RegionDatabase.bindSession(worldRoot)
        try {
            PeriodProcessingStore.bindSession(worldRoot)
            TestPeriodModeStore.bindSession(worldRoot)
            BehaviorStatsStore.bindSession(worldRoot)
        } catch (error: Throwable) {
            PeriodProcessingStore.unbindSession()
            TestPeriodModeStore.unbindSession()
            BehaviorStatsStore.unbindSession()
            RegionDatabase.unbindSession()
            throw error
        }
    }
}

internal fun closeRegionSession() {
    EffectOverlayService.withScopeLifecycle {
        RegionDatabase.unbindSession()
        PeriodProcessingStore.unbindSession()
        TestPeriodModeStore.unbindSession()
        BehaviorStatsStore.unbindSession()
        EffectOverlayService.clearAll()
    }
    clearFlySessionState()
    ImyvmWorldGeo.locationActionBarEnabledPlayers.clear()
    // PlayerRegionEntryExitTracker.playerStates is not cleared here: Fabric disconnects every
    // player before SERVER_STOPPED, and each disconnect already removes that player's state.
}
