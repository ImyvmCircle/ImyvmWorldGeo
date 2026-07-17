package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
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
    val result = mergeRegions(sourceRegion, targetRegion, System.currentTimeMillis()) { saveRegionData(player) }
    val success = when (result) {
        is RegionMergeResult.Success -> result
        RegionMergeResult.PersistenceFailed -> return 0
    }

    PlayerRegionEntryExitTracker.onRegionMerged(sourceRegion, targetRegion)
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.region.merge.success",
            sourceRegion.name, targetRegion.name, success.scopeCount, success.renamedCount
        )!!
    )
    return 1
}

internal sealed interface RegionMergeResult {
    data class Success(val scopeCount: Int, val renamedCount: Int) : RegionMergeResult
    data object PersistenceFailed : RegionMergeResult
}

internal fun mergeRegions(
    sourceRegion: Region,
    targetRegion: Region,
    changedAtMillis: Long,
    save: () -> Boolean
): RegionMergeResult {
    RegionDatabase.requireCanonicalRegions(sourceRegion, targetRegion)
    require(sourceRegion !== targetRegion) { "source and target regions must differ" }
    RegionDatabase.requireMergeableRegionPlayerStats(sourceRegion, targetRegion)

    val sourceScopes = sourceRegion.scopes.toList()
    val targetScopes = targetRegion.scopes.toList()
    val originalNames = sourceScopes.associateWith { it.scopeName }
    val sourceHistory = sourceRegion.ownershipHistorySnapshot()
    val originalTargetHistory = targetRegion.ownershipHistorySnapshot()
    val scopeCount = sourceScopes.size
    var renamedCount = 0
    val rollbackDatabase = RegionDatabase.mergeAndRemoveRegionReversibly(sourceRegion, targetRegion)
    try {
        val retiredScopes = sourceRegion.retireOwnedScopesForMerge()
        check(retiredScopes == sourceScopes) { "retired scopes differ from the validated source snapshot" }
        for (scope in retiredScopes) {
            val resolvedName = resolveTransferScopeName(scope.scopeName, targetRegion)
            if (!resolvedName.equals(scope.scopeName, ignoreCase = false)) renamedCount++
            scope.renameTo(resolvedName)
            targetRegion.addOwnedScope(scope)
        }

        val mergedHistory = originalTargetHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        sourceHistory.forEach { (scopeId, entries) ->
            mergedHistory.getOrPut(scopeId) { mutableListOf() }.addAll(entries)
        }
        sourceScopes.forEach { scope ->
            val scopeId = scope.requireAssignedScopeId()
            mergedHistory.getOrPut(scopeId) { mutableListOf() }.add(
                ScopeOwnershipEntry(
                    scopeId.raw,
                    sourceRegion.numberID,
                    targetRegion.numberID,
                    changedAtMillis
                )
            )
        }
        targetRegion.replaceOwnershipHistory(mergedHistory)
    } catch (error: RuntimeException) {
        originalNames.forEach { (scope, name) -> scope.renameTo(name) }
        sourceRegion.restoreOwnedScopes(sourceScopes)
        targetRegion.restoreOwnedScopes(targetScopes)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        rollbackDatabase()
        throw error
    }

    if (!save()) {
        originalNames.forEach { (scope, name) -> scope.renameTo(name) }
        sourceRegion.restoreOwnedScopes(sourceScopes)
        targetRegion.restoreOwnedScopes(targetScopes)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        rollbackDatabase()
        return RegionMergeResult.PersistenceFailed
    }
    return RegionMergeResult.Success(scopeCount, renamedCount)
}
