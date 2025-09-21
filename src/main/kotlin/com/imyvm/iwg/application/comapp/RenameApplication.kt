package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionRename(player: ServerPlayerEntity, region: Region, newName: String): Int {
    if(!checkNameDigit(newName, player)) return 0
    val oldName = region.name
    if(!checkNameRepeat(oldName, newName, player)) return 0

    return try {
        ImyvmWorldGeo.data.renameRegion(region, newName)
        player.sendMessage(Translator.tr("command.rename.success", oldName, newName))
        1
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("command.rename.duplicate_name", newName))
        0
    }
}

fun onRenameScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String,
    newName: String
): Int {
    val existingScope = targetRegion.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }

    if (existingScope == null) {
        player.sendMessage(Translator.tr("command.scope.scope_not_found", scopeName, targetRegion.name))
        return 0
    }

    if (existingScope.scopeName.equals(newName, ignoreCase = true)) {
        player.sendMessage(Translator.tr("command.scope.rename.repeated_same_name"))
        return 0
    }

    for (scope in targetRegion.geometryScope) {
        if (scope.scopeName.equals(newName, ignoreCase = true)) {
            player.sendMessage(Translator.tr("command.scope.rename.duplicate_scope_name"))
            return 0
        }
    }

    existingScope.scopeName = newName
    player.sendMessage(Translator.tr("command.scope.rename.success", scopeName, newName, targetRegion.name))
    return 1
}

private fun checkNameDigit(newName: String, player: ServerPlayerEntity): Boolean {
    if (newName.matches("\\d+".toRegex())) {
        player.sendMessage(Translator.tr("command.rename.name_is_digits_only"))
        return false
    }
    return true
}

private fun checkNameRepeat(oldName: String, newName: String, player: ServerPlayerEntity): Boolean {
    if (oldName == newName) {
        player.sendMessage(Translator.tr("command.rename.repeated_same_name"))
        return false
    }
    return true
}