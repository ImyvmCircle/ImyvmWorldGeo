package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.display.displayScopeBoundariesForPlayer
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onQueryRegion(player: ServerPlayer, region: Region, isApi: Boolean) : Int{
    val messageKey = if (isApi) {
        "interaction.meta.api.query.result"
    } else {
        "interaction.meta.command.query.result"
    }

    player.sendSystemMessage(
        Translator.tr(messageKey,
            region.name,
            region.numberID.toString(),
            region.calculateTotalArea(),
            region.showOnDynmap)!!
    )

    val server = player.level().server
    region.getSettingInfos(server).forEach { info -> player.sendSystemMessage(info) }
    region.getScopeInfos(server).forEach { info -> player.sendSystemMessage(info) }
    return 1
}

fun onListRegions(player: ServerPlayer): Int {
    val regions = RegionDatabase.getRegionList()
    if (regions.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.command.list.empty")!!)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.command.list.header")!!)
    regions.forEach { region ->
        player.sendSystemMessage(Translator.tr("interaction.meta.command.list.item", region.name, region.numberID)!!)
    }
    return 1
}

fun onToggleActionBar(player: ServerPlayer): Int {
    if (ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.remove(player.uuid)
        player.sendSystemMessage(Translator.tr("interaction.meta.command.toggle.disabled")!!)
    } else {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(player.uuid)
        player.sendSystemMessage(Translator.tr("interaction.meta.command.toggle.enabled")!!)
        val server = player.level().server ?: return 1
        val playerWorld = player.level()
        val scopes = RegionDatabase.getRegionList()
            .flatMap { it.geometryScope }
            .filter { it.geoShape != null && it.getWorld(server) == playerWorld }
        displayScopeBoundariesForPlayer(player, scopes)
    }
    return 1
}

fun onHelp(player: ServerPlayer): Int {
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
        "region.merge",

        // Scopes
        "scope.add",
        "scope.modify",
        "scope.delete",
        "scope.transfer",

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
        "info.help",

        // Dynmap
        "dynmap.toggle",
        "dynmap.toggle_scope"
    )

    Translator.trBase("interaction.meta.command.help", helpOrder).forEach { line ->
        player.sendSystemMessage(line)
    }

    return 1
}