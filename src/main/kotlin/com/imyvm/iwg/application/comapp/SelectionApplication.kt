package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun startSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID] = mutableListOf()
        ImyvmWorldGeo.logger.info("Player $playerUUID has started selection mode, players in selection mod: ${ImyvmWorldGeo.pointSelectingPlayers.keys}")
        player.sendMessage(Translator.tr("command.select.start"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.already"))
        0
    }
}

fun stopSelection(player: ServerPlayerEntity): Int{
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers.remove(playerUUID)
        ImyvmWorldGeo.logger.info("Player $playerUUID has stopped selection mode, players in selection mod: ${ImyvmWorldGeo.pointSelectingPlayers.keys}")
        player.sendMessage(Translator.tr("command.select.stop"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}

fun resetSelection(player: ServerPlayerEntity): Int {
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID]?.clear()
        ImyvmWorldGeo.logger.info("Player $playerUUID has reset their selection, current selected positions: ${ImyvmWorldGeo.pointSelectingPlayers[playerUUID]}")
        player.sendMessage(Translator.tr("command.select.reset"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}