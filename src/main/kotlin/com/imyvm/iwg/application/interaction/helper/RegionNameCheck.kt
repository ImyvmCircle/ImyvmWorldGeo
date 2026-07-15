package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.isValidGeoName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun isValidName(name: String): Boolean = isValidGeoName(name)

fun checkNameEmpty(newName: String, player: ServerPlayer): Boolean{
    if (newName.trim() == "") {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.name_is_empty")!!)
        return false
    }
    return true
}

fun checkNameFormat(newName: String, player: ServerPlayer): Boolean {
    if (!isValidName(newName)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.name_format_invalid")!!)
        return false
    }
    return true
}

fun checkRegionNameUnique(oldName: String = "", newName: String, player: ServerPlayer): Boolean {
    if (oldName.equals(newName, ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.repeated_same_name", newName)!!)
        return false
    } else if (RegionDatabase.getRegionList().any { it.name.equals(newName, ignoreCase = true) }) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.duplicate_name", newName)!!)
        return false
    }
    return true
}

@Deprecated("Use checkRegionNameUnique; Scope names have a separate owner-local namespace")
fun checkNameRepeat(oldName: String = "", newName: String, player: ServerPlayer): Boolean =
    checkRegionNameUnique(oldName, newName, player)
