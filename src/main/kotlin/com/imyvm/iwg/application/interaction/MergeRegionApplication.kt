package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionMerge(
    player: ServerPlayerEntity,
    sourceRegion: Region,
    targetRegion: Region
): Int {
    if (sourceRegion.numberID == targetRegion.numberID) {
        player.sendMessage(Translator.tr("interaction.meta.region.merge.error.same_region"))
        return 0
    }

    var renamedCount = 0
    for (scope in sourceRegion.geometryScope.toList()) {
        val resolvedName = resolveTransferScopeName(scope.scopeName, targetRegion)
        if (!resolvedName.equals(scope.scopeName, ignoreCase = false)) renamedCount++
        scope.scopeName = resolvedName
        targetRegion.geometryScope.add(scope)
    }
    val scopeCount = sourceRegion.geometryScope.size

    RegionDatabase.removeRegion(sourceRegion)

    player.sendMessage(
        Translator.tr(
            "interaction.meta.region.merge.success",
            sourceRegion.name, targetRegion.name, scopeCount, renamedCount
        )
    )
    return 1
}
