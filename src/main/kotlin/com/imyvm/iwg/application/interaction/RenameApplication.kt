package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.interaction.helper.checkNameDigit
import com.imyvm.iwg.application.interaction.helper.checkNameEmpty
import com.imyvm.iwg.application.interaction.helper.checkNameRepeat
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionRename(player: ServerPlayerEntity, region: Region, newName: String): Int {
    if (!checkNameEmpty(newName, player)) return 0
    if(!checkNameDigit(newName, player)) return 0
    val oldName = region.name
    if(!checkNameRepeat(oldName, newName, player)) return 0

    return try {
        RegionDatabase.renameRegion(region, newName)
        player.sendMessage(Translator.tr("interaction.meta.rename.success", oldName, newName))
        1
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("interaction.meta.rename.duplicate_name", newName))
        0
    }
}

fun onScopeRename(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String,
    newName: String
): Int {
    try {
        val existingScope = targetRegion.getScopeByName(scopeName)

        if (existingScope.scopeName.equals(newName, ignoreCase = true)) {
            player.sendMessage(Translator.tr("interaction.meta.scope.rename.repeated_same_name"))
            return 0
        }

        for (scope in targetRegion.geometryScope) {
            if (scope !== existingScope && scope.scopeName.equals(newName, ignoreCase = true)) {
                player.sendMessage(Translator.tr("interaction.meta.scope.rename.duplicate_scope_name"))
                return 0
            }
        }

        existingScope.scopeName = newName
        player.sendMessage(Translator.tr("interaction.meta.scope.rename.success", scopeName, newName, targetRegion.name))
        return 1

    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr(e.message))
        return 0
    }
}