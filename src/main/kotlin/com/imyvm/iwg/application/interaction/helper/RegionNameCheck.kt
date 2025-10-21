package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun checkNameEmpty(newName: String, player: ServerPlayerEntity): Boolean{
    if (newName.trim() == "") {
        player.sendMessage(Translator.tr("interaction.meta.name.name_is_empty"))
        return false
    }
    return true
}

fun checkNameDigit(newName: String, player: ServerPlayerEntity): Boolean {
    if (newName.matches("\\d+".toRegex())) {
        player.sendMessage(Translator.tr("interaction.meta.name.name_is_digits_only"))
        return false
    }
    return true
}

fun checkNameRepeat(oldName: String = "", newName: String, player: ServerPlayerEntity): Boolean {
    if (oldName == newName) {
        player.sendMessage(Translator.tr("interaction.meta.name.repeated_same_name", newName))
        return false
    } else if (RegionDatabase.getRegionList().find { it.name == newName } != null) {
        player.sendMessage(Translator.tr("interaction.meta.name.duplicate_name", newName))
        return false
    }
    return true
}