package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.interaction.helper.checkNameEmpty
import com.imyvm.iwg.application.interaction.helper.checkNameFormat
import com.imyvm.iwg.application.interaction.helper.checkRegionNameUnique
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onRegionRename(player: ServerPlayer, region: Region, newName: String): Int {
    RegionDatabase.requireCanonicalRegion(region)

    val normalizedName = newName.trim()
    if (!checkNameEmpty(normalizedName, player)) return 0
    if (!checkNameFormat(normalizedName, player)) return 0
    val oldName = region.name
    if(!checkRegionNameUnique(oldName, normalizedName, player)) return 0

    return try {
        if (!renameRegion(region, normalizedName) { saveRegionData(player) }) return 0
        player.sendSystemMessage(Translator.tr("interaction.meta.rename.success", oldName, normalizedName))
        1
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name.duplicate_name", normalizedName))
        0
    }
}

fun onScopeRename(
    player: ServerPlayer,
    targetRegion: Region,
    scopeName: String,
    newName: String
): Int {
    RegionDatabase.requireCanonicalRegion(targetRegion)

    val existingScope = getScopeOrNotify(player, targetRegion, scopeName) ?: return 0
    val oldName = existingScope.scopeName
    val normalizedName = newName.trim()

    if (!checkNameEmpty(normalizedName, player)) return 0
    if (!checkNameFormat(normalizedName, player)) return 0

    if (existingScope.scopeName.equals(normalizedName, ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.rename.repeated_same_name"))
        return 0
    }

    for (scope in targetRegion.scopes) {
        if (scope !== existingScope && scope.scopeName.equals(normalizedName, ignoreCase = true)) {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.rename.duplicate_scope_name"))
            return 0
        }
    }

    if (!renameScope(targetRegion, existingScope, normalizedName) { saveRegionData(player) }) return 0
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.rename.success", scopeName, normalizedName, targetRegion.name))
    return 1
}

internal fun renameRegion(region: Region, newName: String, save: () -> Boolean): Boolean {
    RegionDatabase.requireCanonicalRegion(region)
    val oldName = region.name
    RegionDatabase.renameCanonicalRegion(region, newName)
    if (!save()) {
        RegionDatabase.renameCanonicalRegion(region, oldName)
        return false
    }
    return true
}

internal fun renameScope(
    region: Region,
    scope: GeoScope,
    newName: String,
    save: () -> Boolean
): Boolean {
    RegionDatabase.requireCanonicalScope(region, scope)
    val oldName = scope.scopeName
    region.renameOwnedScope(scope, newName)
    if (!save()) {
        region.renameOwnedScope(scope, oldName)
        return false
    }
    return true
}
