package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onQueryRegion(player: ServerPlayerEntity, region: Region, isApi: Boolean) {
    val messageKey = if (isApi) {
        "api.query.result"
    } else {
        "command.query.result"
    }

    player.sendMessage(
        Translator.tr(messageKey,
            region.name,
            region.numberID.toString(),
            region.calculateTotalArea())
    )

    val server = player.server
    region.getSettingInfos(server).forEach { info -> player.sendMessage(info) }
    region.getScopeInfos(server).forEach { info -> player.sendMessage(info) }
}

fun onListRegions(player: ServerPlayerEntity): Int {
    val regions = ImyvmWorldGeo.data.getRegionList()
    if (regions.isEmpty()) {
        player.sendMessage(Translator.tr("command.list.empty"))
        return 0
    }
    val regionList = regions.joinToString("\n") { "Region: ${it.name}, ID: ${it.numberID}" }
    player.sendMessage(Translator.tr("command.list.header", regionList))
    return 1
}

fun onToggleActionBar(player: ServerPlayerEntity): Int {
    if (ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.remove(player.uuid)
        player.sendMessage(Translator.tr("command.toggle.disabled"))
    } else {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(player.uuid)
        player.sendMessage(Translator.tr("command.toggle.enabled"))
    }
    return 1
}

fun onHelp(player: ServerPlayerEntity): Int {
    player.sendMessage(Translator.tr("command.help"))
    return 1
}