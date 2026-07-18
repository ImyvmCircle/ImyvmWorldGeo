package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeOwnershipEntry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onScopeTransfer(
    player: ServerPlayer,
    sourceRegion: Region,
    scopeName: String,
    targetRegion: Region
): Int {
    RegionDatabase.requireCanonicalRegions(sourceRegion, targetRegion)

    if (sourceRegion.numberID == targetRegion.numberID) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.transfer.error.same_region"))
        return 0
    }

    if (sourceRegion.scopes.size < 2) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.transfer.error.last_scope"))
        return 0
    }

    val scope = getScopeOrNotify(player, sourceRegion, scopeName) ?: return 0

    val result = transferScope(sourceRegion, scope, targetRegion, System.currentTimeMillis()) {
        saveRegionData(player)
    }
    val success = when (result) {
        is ScopeTransferResult.Success -> result
        ScopeTransferResult.PersistenceFailed -> return 0
    }

    val originalName = success.originalName
    val resolvedName = success.resolvedName
    val nameChanged = resolvedName != originalName

    if (nameChanged) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.scope.transfer.success.renamed",
                scopeName, resolvedName, sourceRegion.name, targetRegion.name
            )
        )
    } else {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.scope.transfer.success",
                scopeName, sourceRegion.name, targetRegion.name
            )
        )
    }
    return 1
}

internal sealed interface ScopeTransferResult {
    data class Success(val originalName: String, val resolvedName: String) : ScopeTransferResult
    data object PersistenceFailed : ScopeTransferResult
}

internal fun transferScope(
    sourceRegion: Region,
    scope: GeoScope,
    targetRegion: Region,
    changedAtMillis: Long,
    save: () -> Boolean
): ScopeTransferResult {
    RegionDatabase.requireCanonicalRegions(sourceRegion, targetRegion)
    require(sourceRegion !== targetRegion) { "source and target regions must differ" }
    RegionDatabase.requireCanonicalScope(sourceRegion, scope)

    val originalName = scope.scopeName
    val resolvedName = resolveTransferScopeName(originalName, targetRegion)
    val sourceReceipt = sourceRegion.removeOwnedScope(scope)
    val originalSourceHistory = sourceRegion.ownershipHistorySnapshot()
    val originalTargetHistory = targetRegion.ownershipHistorySnapshot()

    try {
        val scopeId = scope.requireAssignedScopeId()
        scope.renameTo(resolvedName)
        targetRegion.addOwnedScope(scope)
        val newSourceHistory = originalSourceHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        val newTargetHistory = originalTargetHistory.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        newSourceHistory.remove(scopeId)?.let { previousEntries ->
            newTargetHistory.getOrPut(scopeId) { mutableListOf() }.addAll(previousEntries)
        }
        newTargetHistory.getOrPut(scopeId) { mutableListOf() }.add(
            ScopeOwnershipEntry(
                scopeId.raw,
                sourceRegion.numberID,
                targetRegion.numberID,
                changedAtMillis
            )
        )
        sourceRegion.replaceOwnershipHistory(newSourceHistory)
        targetRegion.replaceOwnershipHistory(newTargetHistory)
    } catch (error: RuntimeException) {
        if (targetRegion.containsScope(scope)) targetRegion.removeOwnedScope(scope)
        scope.renameTo(originalName)
        sourceRegion.restoreOwnedScope(sourceReceipt)
        sourceRegion.replaceOwnershipHistory(originalSourceHistory)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        throw error
    }
    if (!save()) {
        targetRegion.removeOwnedScope(scope)
        scope.renameTo(originalName)
        sourceRegion.restoreOwnedScope(sourceReceipt)
        sourceRegion.replaceOwnershipHistory(originalSourceHistory)
        targetRegion.replaceOwnershipHistory(originalTargetHistory)
        return ScopeTransferResult.PersistenceFailed
    }
    return ScopeTransferResult.Success(originalName, resolvedName)
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
