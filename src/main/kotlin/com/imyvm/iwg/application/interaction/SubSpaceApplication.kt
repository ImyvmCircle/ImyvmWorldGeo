package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.subSpaceErrorMessage
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.application.selection.getEffectiveShapeType
import com.imyvm.iwg.application.space.WorldGeoSpaceSupport
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoSettingSummary
import com.imyvm.iwg.domain.WorldGeoSettingVisibility
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.TextParser
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onSubSpaceCreationFromSelection(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    name: String,
    shapeType: GeoShapeType?,
    entryMessage: String? = null
): Int {
    RegionDatabase.requireCanonicalScope(region, parentScope)
    if (player.level().dimension().identifier() != parentScope.worldId) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.wrong_world", parentScope.scopeName, region.name)!!)
        return 0
    }
    val state = getCreationSelectionForSubSpace(player) ?: return 0
    val resolvedShapeType = shapeType ?: state.getEffectiveShapeType()
    return when (val result = RegionFactory.createSubSpaceShape(state.points, resolvedShapeType, region, parentScope)) {
        is Result.Ok -> {
            val subSpace = onSubSpaceCreation(player, region, parentScope, name, result.value, entryMessage) ?: return 0
            clearSelectionDisplay(player)
            clearPlayerSelection(player.uuid)
            player.sendSystemMessage(
                Translator.tr("interaction.meta.subspace.create.success", subSpace.name, parentScope.scopeName, region.name, subSpace.subSpaceId)!!
            )
            1
        }
        is Result.Err -> {
            subSpaceErrorMessage(result.error, resolvedShapeType).forEach(player::sendSystemMessage)
            0
        }
    }
}

fun onSubSpaceShapeReplacementFromSelection(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    subSpace: SubSpace,
    shapeType: GeoShapeType?
): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    if (player.level().dimension().identifier() != parentScope.worldId) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.wrong_world", parentScope.scopeName, region.name)!!)
        return 0
    }
    val state = getCreationSelectionForSubSpace(player) ?: return 0
    val resolvedShapeType = shapeType ?: subSpace.geoShape.geoShapeType
    return when (val result = RegionFactory.createSubSpaceShape(state.points, resolvedShapeType, region, parentScope, subSpace)) {
        is Result.Ok -> {
            val replaced = onReplacingSubSpaceShape(player, region, parentScope, subSpace, result.value)
            if (replaced == 1) {
                clearSelectionDisplay(player)
                clearPlayerSelection(player.uuid)
                player.sendSystemMessage(
                    Translator.tr("interaction.meta.subspace.shape_replace.success", subSpace.name, parentScope.scopeName, region.name)!!
                )
            }
            replaced
        }
        is Result.Err -> {
            subSpaceErrorMessage(result.error, resolvedShapeType).forEach(player::sendSystemMessage)
            0
        }
    }
}

fun onSubSpaceCreation(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    name: String,
    shape: GeoShape,
    entryMessage: String? = null,
    stringTags: Set<String> = emptySet(),
    keyedTags: Map<String, String> = emptyMap()
): SubSpace? {
    RegionDatabase.requireCanonicalScope(region, parentScope)
    val subSpace = SubSpace(
        RegionDatabase.nextSubSpaceId(),
        name,
        parentScope.requireAssignedScopeId(),
        parentScope.worldId,
        shape,
        entryMessage,
        stringTags = stringTags,
        keyedTags = keyedTags
    )
    return try {
        region.addSubSpace(subSpace)
        if (!saveRegionData(player)) {
            region.removeSubSpace(subSpace)
            null
        } else subSpace
    } catch (error: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.invalid", error.message ?: "invalid")!!)
        null
    }
}

fun onSubSpaceDelete(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val index = region.removeSubSpace(subSpace)
    if (!saveRegionData(player)) {
        region.restoreSubSpace(index, subSpace)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.delete.success", subSpace.name, parentScope.scopeName, region.name)!!)
    return 1
}

fun onSubSpaceRename(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, newName: String): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val oldName = subSpace.name
    return try {
        region.renameSubSpace(subSpace, newName)
        if (!saveRegionData(player)) {
            region.renameSubSpace(subSpace, oldName)
            0
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.subspace.rename.success", oldName, newName, parentScope.scopeName, region.name)!!)
            1
        }
    } catch (error: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.invalid", error.message ?: "invalid")!!)
        0
    }
}

fun onReplacingSubSpaceShape(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    subSpace: SubSpace,
    newShape: GeoShape
): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val oldShape = subSpace.geoShape
    return try {
        region.replaceSubSpaceGeometry(subSpace, newShape)
        if (!saveRegionData(player)) {
            region.replaceSubSpaceGeometry(subSpace, oldShape)
            0
        } else 1
    } catch (error: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.invalid", error.message ?: "invalid")!!)
        0
    }
}

fun onSettingSubSpaceEntryMessage(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    subSpace: SubSpace,
    message: String?
): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val previous = subSpace.entryMessage
    subSpace.replaceEntryMessage(message)
    if (!saveRegionData(player)) {
        subSpace.replaceEntryMessage(previous)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.entry_message.success", subSpace.name, parentScope.scopeName, region.name)!!)
    return 1
}

fun onQuerySubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val shapeInfo = subSpace.geoShape.getShapeInfo()?.string ?: ""
    val entryMessage = subSpace.entryMessage ?: Translator.raw("notification.subspace.enter", region.name, parentScope.scopeName, subSpace.name).orEmpty()
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.subspace.query.result",
            subSpace.name,
            subSpace.subSpaceId,
            parentScope.scopeName,
            region.name,
            subSpace.worldId.toString(),
            shapeInfo,
            entryMessage,
            subSpace.stringTags.joinToString(", "),
            subSpace.keyedTags.entries.joinToString(", ") { "${it.key}=${it.value}" }
        )!!
    )
    return 1
}

fun onDebugCurrentSpace(player: ServerPlayer): Int {
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), player.blockPosition().x, player.blockPosition().z)
    if (resolved == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.space.none")!!)
        return 0
    }
    val (region, scope, subSpace) = resolved
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.space.here",
            region.name,
            region.numberID,
            scope.scopeName,
            scope.requireAssignedScopeId().raw,
            subSpace?.name ?: "",
            subSpace?.subSpaceId ?: 0L
        )!!
    )
    return 1
}

fun onDebugRegion(player: ServerPlayer, region: Region): Int {
    RegionDatabase.requireCanonicalRegion(region)
    player.sendSystemMessage(
        Translator.tr("interaction.meta.debug.region", region.name, region.numberID, region.scopes.size, region.subSpaces.size)!!
    )
    return 1
}

fun onDebugScope(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    RegionDatabase.requireCanonicalScope(region, scope)
    val childCount = region.subSpaces.count { it.parentScopeId == scope.requireAssignedScopeId() }
    val shapeInfo = scope.geoShape?.getShapeInfo()?.string ?: ""
    player.sendSystemMessage(
        Translator.tr("interaction.meta.debug.scope", scope.scopeName, scope.requireAssignedScopeId().raw, region.name, scope.worldId.toString(), childCount, shapeInfo)!!
    )
    return 1
}

fun onDebugSubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int =
    onQuerySubSpace(player, region, parentScope, subSpace)

fun onDebugCurrentSpaceSnapshot(player: ServerPlayer): Int {
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), player.blockPosition().x, player.blockPosition().z)
    if (resolved == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.space.none")!!)
        return 0
    }
    val (region, scope, subSpace) = resolved
    val snapshot = if (subSpace == null) {
        WorldGeoSpaceSupport.snapshot(region, scope)
    } else {
        WorldGeoSpaceSupport.snapshot(region, scope, subSpace)
    }
    sendDebugSnapshot(player, snapshot)
    return 1
}

fun onDebugRegionSpaceSnapshot(player: ServerPlayer, region: Region): Int {
    sendDebugSnapshot(player, WorldGeoSpaceSupport.snapshot(region))
    return 1
}

fun onDebugScopeSpaceSnapshot(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    sendDebugSnapshot(player, WorldGeoSpaceSupport.snapshot(region, scope))
    return 1
}

fun onDebugSubSpaceSnapshot(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int {
    sendDebugSnapshot(player, WorldGeoSpaceSupport.snapshot(region, parentScope, subSpace))
    return 1
}

fun onDebugRegionSettingSummaries(player: ServerPlayer, region: Region): Int =
    sendDebugSettingSummaries(player, WorldGeoSpaceSupport.settingSummaries(region, WorldGeoSettingVisibility.OP_DEBUG))

fun onDebugScopeSettingSummaries(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    RegionDatabase.requireCanonicalScope(region, scope)
    return sendDebugSettingSummaries(player, WorldGeoSpaceSupport.settingSummaries(scope, WorldGeoSettingVisibility.OP_DEBUG))
}

fun onDebugSubSpaceSettingSummaries(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    return sendDebugSettingSummaries(player, WorldGeoSpaceSupport.settingSummaries(subSpace, WorldGeoSettingVisibility.OP_DEBUG))
}

fun onDebugSendRegionSpaceMessage(player: ServerPlayer, region: Region, rawMessage: String): Int {
    val count = WorldGeoSpaceSupport.sendMessage(player.level().server, region, TextParser.parse(rawMessage))
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.space_message.sent", count)!!)
    return count
}

fun onDebugSendScopeSpaceMessage(player: ServerPlayer, region: Region, scope: GeoScope, rawMessage: String): Int {
    val count = WorldGeoSpaceSupport.sendMessage(player.level().server, region, scope, TextParser.parse(rawMessage))
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.space_message.sent", count)!!)
    return count
}

fun onDebugSendSubSpaceMessage(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, rawMessage: String): Int {
    val count = WorldGeoSpaceSupport.sendMessage(player.level().server, region, parentScope, subSpace, TextParser.parse(rawMessage))
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.space_message.sent", count)!!)
    return count
}

private fun sendDebugSnapshot(player: ServerPlayer, snapshot: WorldGeoSpaceSnapshot) {
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.space_snapshot",
            snapshot.type.name,
            snapshot.id,
            snapshot.name,
            snapshot.dimensionId?.toString() ?: "",
            snapshot.area ?: 0.0,
            snapshot.parentRegionId ?: 0,
            snapshot.parentRegionName ?: "",
            snapshot.parentScopeId ?: 0L,
            snapshot.parentScopeName ?: "",
            snapshot.childScopeCount,
            snapshot.childSubSpaceCount,
            snapshot.stringTags.joinToString(", "),
            snapshot.keyedTags.entries.joinToString(", ") { "${it.key}=${it.value}" },
            snapshot.statsVersion,
            snapshot.dominantBiomeId?.toString() ?: "-",
            snapshot.entryMessageEnabled,
            snapshot.entryMessageConfigured,
            snapshot.mapColorSuggestion?.let { "#" + it.toString(16).padStart(6, '0').uppercase() } ?: "-",
            snapshot.settingSummary.joinToString(", ") { "${it.key}=${it.value}" }
        )!!
    )
}

private fun sendDebugSettingSummaries(player: ServerPlayer, summaries: List<WorldGeoSettingSummary>): Int {
    if (summaries.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.setting_summary.empty")!!)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.setting_summary.header", summaries.size)!!)
    summaries.forEach { summary ->
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.debug.setting_summary.line",
                summary.spaceType.name,
                summary.spaceId,
                summary.settingType,
                summary.key,
                summary.value,
                summary.subject
            )!!
        )
    }
    return summaries.size
}

fun onValidateSubSpaces(player: ServerPlayer): Int {
    val count = RegionDatabase.getRegionList().sumOf { it.subSpaces.size }
    player.sendSystemMessage(Translator.tr("interaction.meta.debug.subspace.validate", count)!!)
    return 1
}

fun onAddingSubSpaceStringTag(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, tag: String): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val added = subSpace.addStringTag(tag)
    if (!added) return 0
    if (!saveRegionData(player)) {
        subSpace.removeStringTag(tag)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.tag.string.add.success", tag, subSpace.name)!!)
    return 1
}

fun onRemovingSubSpaceStringTag(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, tag: String): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val removed = subSpace.removeStringTag(tag)
    if (!removed) return 0
    if (!saveRegionData(player)) {
        subSpace.addStringTag(tag)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.tag.string.remove.success", tag, subSpace.name)!!)
    return 1
}

fun onPuttingSubSpaceKeyedTag(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    subSpace: SubSpace,
    key: String,
    value: String
): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val previous = subSpace.putKeyedTag(key, value)
    if (!saveRegionData(player)) {
        if (previous == null) subSpace.removeKeyedTag(key) else subSpace.putKeyedTag(key, previous)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.tag.keyed.put.success", key, value, subSpace.name)!!)
    return 1
}

fun onRemovingSubSpaceKeyedTag(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, key: String): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    val previous = subSpace.removeKeyedTag(key) ?: return 0
    if (!saveRegionData(player)) {
        subSpace.putKeyedTag(key, previous)
        return 0
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.subspace.tag.keyed.remove.success", key, subSpace.name)!!)
    return 1
}

private fun getCreationSelectionForSubSpace(player: ServerPlayer) =
    ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.takeIf(::isCreationSelection).also {
        if (it == null) player.sendSystemMessage(Translator.tr("interaction.meta.select.create_mode_required")!!)
    }
