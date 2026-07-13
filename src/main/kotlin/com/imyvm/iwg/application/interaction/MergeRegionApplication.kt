package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onRegionMerge(
    player: ServerPlayer,
    sourceRegion: Region,
    targetRegion: Region
): Int {
    if (sourceRegion.numberID == targetRegion.numberID) {
        player.sendSystemMessage(Translator.tr("interaction.meta.region.merge.error.same_region")!!)
        return 0
    }

    val sourceScopes = sourceRegion.scopes.toList()
    val targetScopes = targetRegion.scopes.toList()
    val originalNames = sourceScopes.associateWith { it.scopeName }
    val sourceHistory = sourceRegion.ownershipHistorySnapshot()
    val originalTargetHistory = targetRegion.ownershipHistorySnapshot()
    var renamedCount = 0
    try {
        for (scope in sourceScopes) {
            val resolvedName = resolveTransferScopeName(scope.scopeName, targetRegion)
            if (!resolvedName.equals(scope.scopeName, ignoreCase = false)) renamedCount++
            sourceRegion.removeScope(scope)
            scope.scopeName = resolvedName
            targetRegion.addScope(scope)
        }
    } catch (error: IllegalArgumentException) {
        originalNames.forEach { (scope, name) -> scope.scopeName = name }
        sourceRegion.restoreScopes(sourceScopes)
        targetRegion.restoreScopes(targetScopes)
        throw error
    }
    val scopeCount = sourceScopes.size

    val mergedHistory = originalTargetHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
    sourceHistory.forEach { (scopeId, entries) ->
        mergedHistory.getOrPut(scopeId) { mutableListOf() }.addAll(entries)
    }
    targetRegion.replaceOwnershipHistory(mergedHistory)
    val transferTime = System.currentTimeMillis()
    sourceScopes.forEach { scope ->
        RegionDatabase.recordScopeOwnership(scope.scopeId, sourceRegion, targetRegion, transferTime)
    }

    val rollbackDatabase = RegionDatabase.mergeAndRemoveRegionReversibly(sourceRegion, targetRegion)
    if (!saveRegionData(player)) {
        rollbackDatabase()
        originalNames.forEach { (scope, name) -> scope.scopeName = name }
        sourceRegion.restoreScopes(sourceScopes)
        targetRegion.restoreScopes(targetScopes)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        return 0
    }

    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.region.merge.success",
            sourceRegion.name, targetRegion.name, scopeCount, renamedCount
        )!!
    )
    return 1
}
