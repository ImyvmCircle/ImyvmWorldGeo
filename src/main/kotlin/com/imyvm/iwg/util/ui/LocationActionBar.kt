package com.imyvm.iwg.util.ui

import com.imyvm.iwg.ImyvmWorldGeo
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

fun updateGeographicActionBarForPlayer(player: ServerPlayerEntity) {
    if (!ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) return

    val regionScopePair = ImyvmWorldGeo.playerRegionChecker.getAllRegionScopesWithPlayers()[player.uuid]
    val region = regionScopePair?.first
    val scope = regionScopePair?.second

    val regionPrefix = Translator.tr("scoreboard.region.prefix")?.string ?: "Region:"
    val scopePrefix = Translator.tr("scoreboard.scope.prefix")?.string ?: "Scope:"

    val regionName = region?.name?.takeIf { it.isNotBlank() }
        ?: Translator.tr("scoreboard.region.none.name")?.string ?: "-wilderness-"
    val scopeName = scope?.scopeName?.takeIf { it.isNotBlank() }
        ?: Translator.tr("scoreboard.scope.none.name")?.string ?: "-Free to use-"

    val regionText = if (regionName == Translator.tr("scoreboard.region.none.name")?.string)
        regionName else "$regionPrefix $regionName"
    val scopeText = if (scopeName == Translator.tr("scoreboard.scope.none.name")?.string)
        scopeName else "$scopePrefix $scopeName"

    val fullText = Text.literal("$regionText | $scopeText").formatted(Formatting.GREEN)
    player.sendMessage(fullText, true)
}
