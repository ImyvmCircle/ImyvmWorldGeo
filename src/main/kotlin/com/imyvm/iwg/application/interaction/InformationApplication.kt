package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.display.displayRegionScopeBoundariesForPlayer
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

internal val HELP_TRANSLATION_KEYS = listOf(
    "interaction.meta.command.help.header",

    // Selection
    "interaction.meta.command.help.selection.start",
    "interaction.meta.command.help.selection.stop",
    "interaction.meta.command.help.selection.reset",

    // Region
    "interaction.meta.command.help.region.create",
    "interaction.meta.command.help.region.delete",
    "interaction.meta.command.help.region.rename",
    "interaction.meta.command.help.region.merge",

    // Scopes
    "interaction.meta.command.help.scope.add",
    "interaction.meta.command.help.scope.modify",
    "interaction.meta.command.help.scope.delete",
    "interaction.meta.command.help.scope.transfer",

    // Teleportation
    "interaction.meta.command.help.tp.set",
    "interaction.meta.command.help.tp.reset",
    "interaction.meta.command.help.tp.inquiry",
    "interaction.meta.command.help.tp.teleport",
    "interaction.meta.command.help.tp.toggle",

    // Settings
    "interaction.meta.command.help.setting.add",
    "interaction.meta.command.help.setting.remove",
    "interaction.meta.command.help.setting.query",
    "interaction.meta.command.help.setting_scope.add",
    "interaction.meta.command.help.setting_scope.remove",
    "interaction.meta.command.help.setting_scope.query",

    // Info & General
    "interaction.meta.command.help.info.query",
    "interaction.meta.command.help.info.stats",
    "interaction.meta.command.help.info.list",
    "interaction.meta.command.help.info.toggle",
    "interaction.meta.command.help.info.help",

    // Dynmap
    "interaction.meta.command.help.dynmap.toggle",
    "interaction.meta.command.help.dynmap.toggle_scope"
)

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
            region.showOnDynmap)
    )

    val server = player.level().server
    region.getSettingInfos(server).forEach { info -> player.sendSystemMessage(info) }
    region.getScopeInfos(server).forEach { info -> player.sendSystemMessage(info) }
    return 1
}

fun onListRegions(player: ServerPlayer): Int {
    val regions = RegionDatabase.getRegionList()
    if (regions.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.command.list.empty"))
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.command.list.header"))
    regions.forEach { region ->
        player.sendSystemMessage(Translator.tr("interaction.meta.command.list.item", region.name, region.numberID))
    }
    return 1
}

fun onToggleActionBar(player: ServerPlayer): Int {
    if (ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.remove(player.uuid)
        player.sendSystemMessage(Translator.tr("interaction.meta.command.toggle.disabled"))
    } else {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(player.uuid)
        player.sendSystemMessage(Translator.tr("interaction.meta.command.toggle.enabled"))
        displayRegionScopeBoundariesForPlayer(player, RegionDatabase.getRegionList())
    }
    return 1
}

fun onHelp(player: ServerPlayer): Int {
    Translator.trAll(HELP_TRANSLATION_KEYS).forEach { line ->
        player.sendSystemMessage(line)
    }

    return 1
}
