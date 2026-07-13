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
    region.scopes.forEach { EffectOverlayService.clearScope(it.requireAssignedScopeId()) }
    if (isApi.not()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.delete.success", regionName, regionId)!!)
    }
}

fun onScopeDelete(player: ServerPlayer, region: Region, scopeName: String){
    if (!checkScopeSize(player, region)) return

    try {
        val existingScope = region.getScopeByName(scopeName)
        val index = region.removeScope(existingScope)
        if (!saveRegionData(player)) {
            region.restoreScope(index, existingScope)
            return
        }
        EffectOverlayService.clearScope(existingScope.requireAssignedScopeId())
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name)!!)
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
        return
    }
}

private fun checkScopeSize(player: ServerPlayer, region: Region): Boolean{
    if (region.scopes.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.error.last_scope")!!)
        return false
    }
    return true
}
