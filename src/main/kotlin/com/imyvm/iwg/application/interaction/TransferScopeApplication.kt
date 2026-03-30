package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onScopeTransfer(
    player: ServerPlayer,
    sourceRegion: Region,
    scopeName: String,
    targetRegion: Region
): Int {
    if (sourceRegion.numberID == targetRegion.numberID) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.transfer.error.same_region")!!)
        return 0
    }

    if (sourceRegion.geometryScope.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.transfer.error.last_scope")!!)
        return 0
    }

    val scope = try {
        sourceRegion.getScopeByName(scopeName)
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
        return 0
    }

    val resolvedName = resolveTransferScopeName(scope.scopeName, targetRegion)
    val nameChanged = !resolvedName.equals(scope.scopeName, ignoreCase = false)

    sourceRegion.geometryScope.remove(scope)
    scope.scopeName = resolvedName
    targetRegion.geometryScope.add(scope)

    if (nameChanged) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.scope.transfer.success.renamed",
                scopeName, resolvedName, sourceRegion.name, targetRegion.name
            )!!
        )
    } else {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.scope.transfer.success",
                scopeName, sourceRegion.name, targetRegion.name
            )!!
        )
    }
    return 1
}

internal fun resolveTransferScopeName(originalName: String, targetRegion: Region): String {
    if (targetRegion.geometryScope.none { it.scopeName.equals(originalName, ignoreCase = true) }) {
        return originalName
    }
    var counter = 1
    while (true) {
        val candidate = "$originalName$counter"
        if (targetRegion.geometryScope.none { it.scopeName.equals(candidate, ignoreCase = true) }) {
            return candidate
        }
        counter++
    }
}