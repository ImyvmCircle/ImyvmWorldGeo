package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onTogglingRegionDynmap(player: ServerPlayerEntity, region: Region) {
    region.showOnDynmap = !region.showOnDynmap
    RegionDatabase.save()
    player.sendMessage(Translator.tr("interaction.meta.region.dynmap.toggle", region.name, region.showOnDynmap))
}

fun onTogglingScopeDynmap(player: ServerPlayerEntity, region: Region, scope: GeoScope): Int {
    scope.showOnDynmap = !scope.showOnDynmap
    RegionDatabase.save()
    player.sendMessage(Translator.tr("interaction.meta.scope.dynmap.toggle", region.name, scope.scopeName, scope.showOnDynmap))
    return 1
}
