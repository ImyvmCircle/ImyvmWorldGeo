package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import com.imyvm.iwg.application.region.effect.EffectOverlayService

fun onRegionDelete(player: ServerPlayer, region: Region, isApi: Boolean = false){
    val regionName = region.name
    val regionId = region.numberID
    val deletedScopes = region.scopes
    val deleted = EffectOverlayService.withScopeLifecycle {
        val rollback = RegionDatabase.removeRegionReversibly(region)
        if (!saveRegionData(player)) {
            rollback()
            false
        } else {
            deletedScopes.forEach { EffectOverlayService.clearScope(it.requireAssignedScopeId()) }
            true
        }
    }
    if (!deleted) return

    clearSelectionsReferencing(deletedScopes)
    if (isApi.not()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.delete.success", regionName, regionId)!!)
    }
}

fun onScopeDelete(player: ServerPlayer, region: Region, scopeName: String){
    RegionDatabase.requireCanonicalRegion(region)

    val existingScope = getScopeOrNotify(player, region, scopeName) ?: return
    if (!checkScopeSize(player, region)) return
    val deleted = EffectOverlayService.withScopeLifecycle {
        val index = region.removeScopeFromOwner(existingScope)
        if (!saveRegionData(player)) {
            region.restoreScopeFromOwner(index, existingScope)
            false
        } else {
            EffectOverlayService.clearScope(existingScope.requireAssignedScopeId())
            true
        }
    }
    if (!deleted) return

    clearSelectionsReferencing(listOf(existingScope))
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name)!!)
}

private fun checkScopeSize(player: ServerPlayer, region: Region): Boolean{
    if (region.scopes.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.error.last_scope")!!)
        return false
    }
    return true
}
