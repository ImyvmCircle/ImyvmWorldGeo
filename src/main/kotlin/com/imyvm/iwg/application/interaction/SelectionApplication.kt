package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onStartSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID] = mutableListOf()
        player.sendMessage(Translator.tr("interaction.meta.select.start"))
        1
    } else {
        player.sendMessage(Translator.tr("interaction.meta.select.already"))
        0
    }
}

fun onStopSelection(player: ServerPlayerEntity): Int{
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers.remove(playerUUID)
        player.sendMessage(Translator.tr("interaction.meta.select.stop"))
        1
    } else {
        player.sendMessage(Translator.tr("interaction.meta.select.not_in_mode"))
        0
    }
}

fun onResetSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID]?.clear()
        player.sendMessage(Translator.tr("interaction.meta.select.reset"))
        1
    } else {
        player.sendMessage(Translator.tr("interaction.meta.select.not_in_mode"))
        0
    }
}