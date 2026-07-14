package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import com.imyvm.iwg.application.region.effect.EffectOverlayService

fun onRegionDelete(player: ServerPlayer, region: Region, isApi: Boolean = false){
    val regionName = region.name
    val regionId = region.numberID
    val rollback = RegionDatabase.removeRegionReversibly(region)
    if (!saveRegionData(player)) {
        rollback()
        return
    }
    clearSelectionsReferencing(region.scopes)
    region.scopes.forEach { EffectOverlayService.clearScope(it.requireAssignedScopeId()) }
    if (isApi.not()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.delete.success", regionName, regionId)!!)
    }
}

fun onScopeDelete(player: ServerPlayer, region: Region, scopeName: String){
    RegionDatabase.requireCanonicalRegion(region)

    val existingScope = getScopeOrNotify(player, region, scopeName) ?: return
    if (!checkScopeSize(player, region)) return
    val index = region.removeScope(existingScope)
    if (!saveRegionData(player)) {
        region.restoreScope(index, existingScope)
        return
    }
    clearSelectionsReferencing(listOf(existingScope))
    EffectOverlayService.clearScope(existingScope.requireAssignedScopeId())
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name)!!)
}

private fun checkScopeSize(player: ServerPlayer, region: Region): Boolean{
    if (region.scopes.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.error.last_scope")!!)
        return false
    }
    return true
}
