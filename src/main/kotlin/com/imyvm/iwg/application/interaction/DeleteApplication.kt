package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.RegionDeleteResult
import com.imyvm.iwg.inter.api.ScopeDeleteResult
import net.minecraft.server.level.ServerPlayer
import com.imyvm.iwg.application.region.effect.EffectOverlayService

fun onRegionDelete(player: ServerPlayer, region: Region): RegionDeleteResult =
    deleteRegion(region) { saveRegionData(player) }

internal fun deleteRegion(
    region: Region,
    save: () -> Boolean
): RegionDeleteResult {
    RegionDatabase.requireCanonicalRegion(region)
    val deletedScopes = region.scopes
    val deleted = EffectOverlayService.withScopeLifecycle {
        val rollback = RegionDatabase.removeRegionReversibly(region)
        if (!save()) {
            rollback()
            false
        } else {
            PlayerRegionEntryExitTracker.onRegionDeleted(region)
            deletedScopes.forEach { EffectOverlayService.clearScope(it.requireAssignedScopeId()) }
            true
        }
    }
    if (!deleted) return RegionDeleteResult.PERSISTENCE_FAILED

    clearSelectionsReferencing(deletedScopes)
    return RegionDeleteResult.SUCCESS
}

fun onScopeDelete(player: ServerPlayer, region: Region, scope: GeoScope): ScopeDeleteResult =
    deleteScope(region, scope) { saveRegionData(player) }

internal fun deleteScope(
    region: Region,
    scope: GeoScope,
    save: () -> Boolean
): ScopeDeleteResult {
    val result = EffectOverlayService.withScopeLifecycle {
        RegionDatabase.requireCanonicalScope(region, scope)
        if (region.scopes.size == 1) return@withScopeLifecycle ScopeDeleteResult.LAST_SCOPE
        val receipt = region.removeOwnedScope(scope)
        if (!save()) {
            region.restoreOwnedScope(receipt)
            ScopeDeleteResult.PERSISTENCE_FAILED
        } else {
            EffectOverlayService.clearScope(scope.requireAssignedScopeId())
            ScopeDeleteResult.SUCCESS
        }
    }
    if (result == ScopeDeleteResult.SUCCESS) clearSelectionsReferencing(listOf(scope))
    return result
}
