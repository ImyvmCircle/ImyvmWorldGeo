package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onTogglingRegionDynmap(player: ServerPlayer, region: Region) {
    if (!toggleRegionDynmap(region) { saveRegionData(player) }) return
    player.sendSystemMessage(Translator.tr("interaction.meta.region.dynmap.toggle", region.name, region.showOnDynmap))
}

fun onTogglingScopeDynmap(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    if (!toggleScopeDynmap(region, scope) { saveRegionData(player) }) return 0
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.dynmap.toggle", region.name, scope.scopeName, scope.showOnDynmap))
    return 1
}

internal fun toggleRegionDynmap(region: Region, save: () -> Boolean): Boolean {
    RegionDatabase.requireCanonicalRegion(region)
    val oldValue = region.showOnDynmap
    region.setDynmapVisibility(!oldValue)
    if (!save()) {
        region.setDynmapVisibility(oldValue)
        return false
    }
    return true
}

internal fun toggleScopeDynmap(region: Region, scope: GeoScope, save: () -> Boolean): Boolean {
    RegionDatabase.requireCanonicalScope(region, scope)
    val oldValue = scope.showOnDynmap
    scope.setDynmapVisibility(!oldValue)
    if (!save()) {
        scope.setDynmapVisibility(oldValue)
        return false
    }
    return true
}
