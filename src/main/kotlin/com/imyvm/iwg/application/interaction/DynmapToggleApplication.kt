package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onTogglingRegionDynmap(player: ServerPlayer, region: Region) {
    val oldValue = region.showOnDynmap
    region.showOnDynmap = !oldValue
    if (!saveRegionData(player)) {
        region.showOnDynmap = oldValue
        return
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.region.dynmap.toggle", region.name, region.showOnDynmap)!!)
}

fun onTogglingScopeDynmap(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    require(region.containsScope(scope)) { "scope does not belong to region" }
    val oldValue = scope.showOnDynmap
    scope.setDynmapVisibility(!oldValue)
    if (!saveRegionData(player)) {
        scope.setDynmapVisibility(oldValue)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.dynmap.toggle", region.name, scope.scopeName, scope.showOnDynmap)!!)
    return 1
}
