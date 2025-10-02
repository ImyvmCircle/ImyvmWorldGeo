package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.ui.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onStartSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID] = mutableListOf()
        player.sendMessage(Translator.tr("command.select.start"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.already"))
        0
    }
}

fun onStopSelection(player: ServerPlayerEntity): Int{
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers.remove(playerUUID)
        player.sendMessage(Translator.tr("command.select.stop"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}

fun onResetSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID]?.clear()
        player.sendMessage(Translator.tr("command.select.reset"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}