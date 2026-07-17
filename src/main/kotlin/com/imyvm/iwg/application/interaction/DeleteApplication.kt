package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.DeleteResult
import net.minecraft.server.level.ServerPlayer
import com.imyvm.iwg.application.region.effect.EffectOverlayService

fun onRegionDelete(player: ServerPlayer, region: Region): DeleteResult {
    val deletedScopes = region.scopes
    val deleted = EffectOverlayService.withScopeLifecycle {
        val rollback = RegionDatabase.removeRegionReversibly(region)
        if (!saveRegionData(player)) {
            rollback()
            false
        } else {
            PlayerRegionEntryExitTracker.onRegionDeleted(region)
            deletedScopes.forEach { EffectOverlayService.clearScope(it.requireAssignedScopeId()) }
            true
        }
    }
    if (!deleted) return DeleteResult.PERSISTENCE_FAILED

    clearSelectionsReferencing(deletedScopes)
    return DeleteResult.SUCCESS
}

fun onScopeDelete(player: ServerPlayer, region: Region, scope: GeoScope): DeleteResult {
    RegionDatabase.requireCanonicalRegion(region)

    val deleted = EffectOverlayService.withScopeLifecycle {
        val index = region.removeScope(scope)
        if (!saveRegionData(player)) {
            region.restoreScope(index, scope)
            false
        } else {
            EffectOverlayService.clearScope(scope.requireAssignedScopeId())
            true
        }
    }
    if (!deleted) return DeleteResult.PERSISTENCE_FAILED

    clearSelectionsReferencing(listOf(scope))
    return DeleteResult.SUCCESS
}
