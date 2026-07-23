package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.display.displayRegionScopeBoundariesForPlayer
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
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
        displayRegionScopeBoundariesForPlayer(player, RegionDatabase.getRegionList())
    }
    return 1
}

private const val HELP_PAGE_SIZE = 5

fun onHelp(player: ServerPlayer, pageRaw: String? = null): Int {
    val helpOrder = listOf(
        // Selection
        "selection.start",
        "selection.stop",
        "selection.reset",
        "selection.shape",
        "selection.modify_scope",

        // Region
        "region.create",
        "region.delete",
        "region.rename",
        "region.merge",

        // Scopes
        "scope.select",
        "scope.create",
        "scope.modify",
        "scope.rename",
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
        "scope.setting.add",
        "scope.setting.remove",
        "scope.setting.query",
        "subspace.setting.add",
        "subspace.setting.remove",
        "subspace.setting.query",

        // SubSpaces
        "subspace.select",
        "subspace.create",
        "subspace.delete",
        "subspace.rename",
        "subspace.modify",
        "subspace.query",
        "subspace.tag",

        // Debug
        "debug.space_here",
        "debug.time",
        "debug.behavior",
        "debug.geography",
        "debug.region",
        "debug.scope",
        "debug.subspace",
        "debug.validate_subspaces",

        // Info & General
        "info.query",
        "info.stats",
        "info.list",
        "info.toggle",
        "info.help",

        // Dynmap
        "dynmap.toggle",
        "dynmap.toggle_scope"
    )

    val totalPages = ((helpOrder.size + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE).coerceAtLeast(1)
    val page = (pageRaw?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
    val pageKeys = helpOrder.drop((page - 1) * HELP_PAGE_SIZE).take(HELP_PAGE_SIZE)

    player.sendSystemMessage(Translator.tr("interaction.meta.command.help.header")!!)
    player.sendSystemMessage(Translator.tr("interaction.meta.command.help.page", page, totalPages)!!)
    Translator.trBase("interaction.meta.command.help", pageKeys).forEach { line ->
        player.sendSystemMessage(line)
    }
    player.sendSystemMessage(buildHelpNavigation(page, totalPages))

    return 1
}

private fun buildHelpNavigation(page: Int, totalPages: Int): Component {
    val message = Component.literal("")
    if (page > 1) {
        message.append(helpButton("interaction.meta.command.help.previous", page - 1))
    } else {
        message.append(Translator.tr("interaction.meta.command.help.previous.disabled")!!)
    }
    message.append(Component.literal(" "))
    if (page < totalPages) {
        message.append(helpButton("interaction.meta.command.help.next", page + 1))
    } else {
        message.append(Translator.tr("interaction.meta.command.help.next.disabled")!!)
    }
    return message
}

private fun helpButton(key: String, page: Int): Component {
    val command = "/imyvmWorldGeo help $page"
    return Translator.tr(key)!!.copy().setStyle(
        Style.EMPTY
            .withColor(TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
            .withClickEvent(ClickEvent.RunCommand(command))
            .withHoverEvent(HoverEvent.ShowText(Translator.tr("interaction.meta.command.help.hover", command)!!))
    )
}
