package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeOwnershipEntry
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onRegionMerge(
    player: ServerPlayer,
    sourceRegion: Region,
    targetRegion: Region
): Int {
    RegionDatabase.requireCanonicalRegions(sourceRegion, targetRegion)

    if (sourceRegion.numberID == targetRegion.numberID) {
        player.sendSystemMessage(Translator.tr("interaction.meta.region.merge.error.same_region")!!)
        return 0
    }
    RegionDatabase.requireMergeableRegionPlayerStats(sourceRegion, targetRegion)

    val sourceScopes = sourceRegion.scopes.toList()
    val targetScopes = targetRegion.scopes.toList()
    val originalNames = sourceScopes.associateWith { it.scopeName }
    val sourceHistory = sourceRegion.ownershipHistorySnapshot()
    val originalTargetHistory = targetRegion.ownershipHistorySnapshot()
    val scopeCount = sourceScopes.size
    var renamedCount = 0
    val rollbackDatabase = try {
        for (scope in sourceScopes) {
            val resolvedName = resolveTransferScopeName(scope.scopeName, targetRegion)
            if (!resolvedName.equals(scope.scopeName, ignoreCase = false)) renamedCount++
            sourceRegion.removeScopeFromOwner(scope)
            scope.renameTo(resolvedName)
            targetRegion.addScopeFromOwner(scope)
        }

        val mergedHistory = originalTargetHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        sourceHistory.forEach { (scopeId, entries) ->
            mergedHistory.getOrPut(scopeId) { mutableListOf() }.addAll(entries)
        }
        val transferTime = System.currentTimeMillis()
        sourceScopes.forEach { scope ->
            val scopeId = scope.requireAssignedScopeId()
            mergedHistory.getOrPut(scopeId) { mutableListOf() }.add(
                ScopeOwnershipEntry(
                    scopeId.raw,
                    sourceRegion.numberID,
                    targetRegion.numberID,
                    transferTime
                )
            )
        }
        targetRegion.replaceOwnershipHistory(mergedHistory)
        RegionDatabase.mergeAndRemoveRegionReversibly(sourceRegion, targetRegion)
    } catch (error: RuntimeException) {
        originalNames.forEach { (scope, name) -> scope.renameTo(name) }
        sourceRegion.restoreScopes(sourceScopes)
        targetRegion.restoreScopes(targetScopes)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        throw error
    }

    if (!saveRegionData(player)) {
        rollbackDatabase()
        originalNames.forEach { (scope, name) -> scope.renameTo(name) }
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
