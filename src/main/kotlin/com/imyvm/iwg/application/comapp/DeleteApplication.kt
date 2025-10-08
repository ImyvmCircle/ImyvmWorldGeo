package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionDelete(player: ServerPlayerEntity, region: Region){
    val regionName = region.name
    val regionId = region.numberID
    RegionDatabase.removeRegion(region)
    player.sendMessage(Translator.tr("interaction.meta.delete.success", regionName, regionId))
}

fun onScopeDelete(player: ServerPlayerEntity, region: Region, scopeName: String){
    if (!checkScopeSize(player, region)) return

    try {
        val existingScope = region.getScopeByName(scopeName)
        region.geometryScope.remove(existingScope)
        player.sendMessage(Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name))
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr(e.message))
        return
    }
}

private fun checkScopeSize(player: ServerPlayerEntity, region: Region): Boolean{
    if (region.geometryScope.size < 2) {
        player.sendMessage(Translator.tr("interaction.meta.scope.delete.error.last_scope"))
        return false
    }
    return true
}