package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

private val ALLOWED_NAME_REGEX = Regex("""^[A-Za-z0-9\u00C0-\u024F\u0400-\u04FF\u0600-\u06FF\u4E00-\u9FFF\u3400-\u4DBF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF\u1100-\u11FF\u0370-\u03FF' ]+$""")
private val HAS_LETTER_REGEX = Regex("""[A-Za-z\u00C0-\u024F\u0400-\u04FF\u0600-\u06FF\u4E00-\u9FFF\u3400-\u4DBF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF\u1100-\u11FF\u0370-\u03FF]""")

fun isValidName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    if (!ALLOWED_NAME_REGEX.matches(trimmed)) return false
    if (trimmed.contains("''") || trimmed.contains("  ")) return false
    if (!HAS_LETTER_REGEX.containsMatchIn(trimmed)) return false
    return true
}

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

fun checkNameRepeat(oldName: String = "", newName: String, player: ServerPlayer): Boolean {
    if (oldName == newName) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.repeated_same_name", newName)!!)
        return false
    } else if (RegionDatabase.getRegionList().find { it.name == newName } != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.duplicate_name", newName)!!)
        return false
    }
    return true
}