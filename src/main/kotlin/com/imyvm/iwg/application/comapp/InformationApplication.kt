package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onQueryRegion(player: ServerPlayerEntity, region: Region, isApi: Boolean) : Int{
    val messageKey = if (isApi) {
        "interaction.meta.api.query.result"
    } else {
        "interaction.meta.command.query.result"
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
    return 1
}

fun onListRegions(player: ServerPlayerEntity): Int {
    val regions = RegionDatabase.getRegionList()
    if (regions.isEmpty()) {
        player.sendMessage(Translator.tr("interaction.meta.command.list.empty"))
        return 0
    }
    val regionList = regions.joinToString("\n") { "Region: ${it.name}, ID: ${it.numberID}" }
    player.sendMessage(Translator.tr("interaction.meta.command.list.header", regionList))
    return 1
}

fun onToggleActionBar(player: ServerPlayerEntity): Int {
    if (ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.remove(player.uuid)
        player.sendMessage(Translator.tr("interaction.meta.command.toggle.disabled"))
    } else {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(player.uuid)
        player.sendMessage(Translator.tr("interaction.meta.command.toggle.enabled"))
    }
    return 1
}

fun onHelp(player: ServerPlayerEntity): Int {
    player.sendMessage(Translator.tr("interaction.meta.command.help"))
    return 1
}