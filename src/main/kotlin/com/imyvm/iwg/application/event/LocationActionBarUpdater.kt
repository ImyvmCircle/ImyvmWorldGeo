package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.PlayerRegionChecker
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.domain.Region
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

fun updateGeographicActionBarForPlayer(player: ServerPlayer) {
    if (!isActionBarEnabled(player)) return

    val (region, scope, subSpace) = getPlayerLocation(player)
    sendActionBar(player, buildLocationActionBarText(region, scope, subSpace))
}

private fun isActionBarEnabled(player: ServerPlayer) =
    ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)

private fun getPlayerLocation(player: ServerPlayer): Triple<Region?, GeoScope?, SubSpace?> =
    PlayerRegionChecker.getAllRegionScopesWithPlayers()[player.uuid] ?: Triple(null, null, null)

internal fun buildLocationActionBarText(region: Region?, scope: GeoScope?, subSpace: SubSpace?): String =
    buildLocationActionBarText(region?.name, scope?.scopeName, subSpace?.name)

internal fun buildLocationActionBarText(regionName: String?, scopeName: String?, subSpaceName: String?): String {
    val regionPrefix = Translator.tr("scoreboard.region.prefix")?.string ?: "Region:"
    val scopePrefix = Translator.tr("scoreboard.scope.prefix")?.string ?: "Scope:"
    val subSpacePrefix = Translator.tr("scoreboard.subspace.prefix")?.string ?: "SubSpace:"
    val emptyRegionName = Translator.tr("scoreboard.region.none.name")?.string ?: "-wilderness-"

    val parts = mutableListOf(regionName?.takeIf { it.isNotBlank() }?.let { "$regionPrefix $it" } ?: emptyRegionName)
    scopeName?.takeIf { it.isNotBlank() }?.let { parts.add("$scopePrefix $it") }
    subSpaceName?.takeIf { it.isNotBlank() }?.let { parts.add("$subSpacePrefix $it") }
    return parts.joinToString(" | ")
}

private fun sendActionBar(player: ServerPlayer, message: String) {
    val text = Component.literal(message).withStyle(ChatFormatting.GREEN)
    player.sendOverlayMessage(text)
}
