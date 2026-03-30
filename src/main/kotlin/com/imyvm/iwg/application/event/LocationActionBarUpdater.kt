package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.PlayerRegionChecker
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.domain.Region
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

fun updateGeographicActionBarForPlayer(player: ServerPlayer) {
    if (!isActionBarEnabled(player)) return

    val (region, scope) = getPlayerRegionScope(player)
    val (regionText, scopeText) = buildRegionScopeText(region, scope)

    sendActionBar(player, "$regionText | $scopeText")
}

private fun isActionBarEnabled(player: ServerPlayer) =
    ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)

private fun getPlayerRegionScope(player: ServerPlayer): Pair<Region?, GeoScope?> {
    val pair = PlayerRegionChecker.getAllRegionScopesWithPlayers()[player.uuid]
    return pair?.first to pair?.second
}

private fun buildRegionScopeText(region: Region?, scope: GeoScope?): Pair<String, String> {
    val regionPrefix = Translator.tr("scoreboard.region.prefix")!!?.string ?: "Region:"
    val scopePrefix = Translator.tr("scoreboard.scope.prefix")!!?.string ?: "Scope:"

    val regionName = region?.name?.takeIf { it.isNotBlank() }
        ?: Translator.tr("scoreboard.region.none.name")!!?.string ?: "-wilderness-"
    val scopeName = scope?.scopeName?.takeIf { it.isNotBlank() }
        ?: Translator.tr("scoreboard.scope.none.name")!!?.string ?: "-Free to use-"

    val regionText = if (regionName == Translator.tr("scoreboard.region.none.name")!!?.string) regionName else "$regionPrefix $regionName"
    val scopeText = if (scopeName == Translator.tr("scoreboard.scope.none.name")!!?.string) scopeName else "$scopePrefix $scopeName"
    return regionText to scopeText
}

private fun sendActionBar(player: ServerPlayer, message: String) {
    val text = Component.literal(message).withStyle(ChatFormatting.GREEN)
    player.sendOverlayMessage(text)
}
