package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
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

    if (sourceRegion.scopes.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.transfer.error.last_scope")!!)
        return 0
    }

    val scope = try {
        sourceRegion.getScopeByName(scopeName)
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
        return 0
    }

    val originalName = scope.scopeName
    val resolvedName = resolveTransferScopeName(originalName, targetRegion)
    val nameChanged = !resolvedName.equals(originalName, ignoreCase = false)
    val sourceIndex = sourceRegion.removeScope(scope)
    val originalSourceHistory = sourceRegion.ownershipHistorySnapshot()
    val originalTargetHistory = targetRegion.ownershipHistorySnapshot()

    val transferTime = System.currentTimeMillis()
    try {
        val scopeId = scope.requireAssignedScopeId()
        scope.scopeName = resolvedName
        targetRegion.addScope(scope)
        val newSourceHistory = originalSourceHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        val newTargetHistory = originalTargetHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        newSourceHistory.remove(scopeId)?.let { previousEntries ->
            newTargetHistory.getOrPut(scopeId) { mutableListOf() }.addAll(previousEntries)
        }
        sourceRegion.replaceOwnershipHistory(newSourceHistory)
        targetRegion.replaceOwnershipHistory(newTargetHistory)
        RegionDatabase.recordAssignedScopeOwnership(scopeId, sourceRegion, targetRegion, transferTime)
    } catch (error: IllegalArgumentException) {
        if (targetRegion.containsScope(scope)) targetRegion.removeScope(scope)
        scope.scopeName = originalName
        sourceRegion.restoreScope(sourceIndex, scope)
        sourceRegion.replaceOwnershipHistory(originalSourceHistory)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        throw error
    }
    if (!saveRegionData(player)) {
        targetRegion.removeScope(scope)
        scope.scopeName = originalName
        sourceRegion.restoreScope(sourceIndex, scope)
        sourceRegion.replaceOwnershipHistory(originalSourceHistory)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        return 0
    }

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
    if (targetRegion.scopes.none { it.scopeName.equals(originalName, ignoreCase = true) }) {
        return originalName
    }
    var counter = 1
    while (true) {
        val candidate = "$originalName$counter"
        if (targetRegion.scopes.none { it.scopeName.equals(candidate, ignoreCase = true) }) {
            return candidate
        }
        counter++
    }
}
