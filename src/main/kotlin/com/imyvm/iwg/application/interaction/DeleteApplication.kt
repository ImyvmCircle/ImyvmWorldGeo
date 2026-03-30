package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onRegionDelete(player: ServerPlayer, region: Region, isApi: Boolean = false){
    val regionName = region.name
    val regionId = region.numberID
    RegionDatabase.removeRegion(region)
    if (isApi.not()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.delete.success", regionName, regionId)!!)
    }
}

fun onScopeDelete(player: ServerPlayer, region: Region, scopeName: String){
    if (!checkScopeSize(player, region)) return

    try {
        val existingScope = region.getScopeByName(scopeName)
        region.geometryScope.remove(existingScope)
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name)!!)
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
        return
    }
}

private fun checkScopeSize(player: ServerPlayer, region: Region): Boolean{
    if (region.geometryScope.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.delete.error.last_scope")!!)
        return false
    }
    return true
}