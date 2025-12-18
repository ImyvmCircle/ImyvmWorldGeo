package com.imyvm.iwg.application.interaction

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
    val helpOrder = listOf(
        "header",

        // Selection
        "selection.start",
        "selection.stop",
        "selection.reset",

        // Region
        "region.create",
        "region.delete",
        "region.rename",

        // Scopes
        "scope.add",
        "scope.modify",
        "scope.delete",

        // Teleportation
        "tp.set",
        "tp.reset",
        "tp.inquiry",
        "tp.teleport",
        "tp.toggle",

        // Settings
        "setting.add",
        "setting.remove",
        "setting.query",
        "setting_scope.add",
        "setting_scope.remove",
        "setting_scope.query",

        // Info & General
        "info.query",
        "info.list",
        "info.toggle",
        "info.help"
    )

    Translator.trBase("interaction.meta.command.help", helpOrder).forEach { line ->
        player.sendMessage(line, false)
    }

    return 1
}