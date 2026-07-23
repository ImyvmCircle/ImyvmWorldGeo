package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.buildModifyStartMessage
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

internal enum class ModifySelectionTargetError {
    INVALID_TARGET,
    WRONG_WORLD
}

internal fun isCreationSelection(state: SelectionState): Boolean =
    state.hypotheticalShape !is HypotheticalShape.ModifyExisting

internal fun isSubSpaceSelection(state: SelectionState): Boolean =
    state.hypotheticalShape is HypotheticalShape.SubSpace

internal fun isModifySelectionFor(state: SelectionState, scope: GeoScope): Boolean =
    (state.hypotheticalShape as? HypotheticalShape.ModifyExisting)?.scope === scope

internal fun resetSelectionState(state: SelectionState, shapeType: GeoShapeType?): Boolean {
    if (shapeType != null && !isCreationSelection(state)) return false
    state.points.clear()
    if (shapeType != null) {
        state.hypotheticalShape = when (val current = state.hypotheticalShape) {
            is HypotheticalShape.SubSpace -> current.copy(shapeType = shapeType)
            else -> HypotheticalShape.Normal(shapeType)
        }
    }
    return true
}

internal fun validateModifySelectionStartTarget(
    scope: GeoScope,
    playerWorldId: Identifier,
    findScope: (AssignedScopeId) -> Pair<Region, GeoScope>? = RegionDatabase::getScopeByAssignedId
): ModifySelectionTargetError? {
    val scopeId = scope.assignedScopeIdOrNull ?: return ModifySelectionTargetError.INVALID_TARGET
    val canonicalScope = findScope(scopeId)?.second
    return validateModifySelectionScope(scope, canonicalScope, playerWorldId)
}

internal fun validateModifySelectionTarget(
    region: Region,
    scope: GeoScope,
    playerWorldId: Identifier,
    findScope: (AssignedScopeId) -> Pair<Region, GeoScope>? = RegionDatabase::getScopeByAssignedId
): ModifySelectionTargetError? {
    val scopeId = scope.assignedScopeIdOrNull ?: return ModifySelectionTargetError.INVALID_TARGET
    val canonicalTarget = findScope(scopeId) ?: return ModifySelectionTargetError.INVALID_TARGET
    if (canonicalTarget.first !== region) return ModifySelectionTargetError.INVALID_TARGET
    return validateModifySelectionScope(scope, canonicalTarget.second, playerWorldId)
}

private fun validateModifySelectionScope(
    scope: GeoScope,
    canonicalScope: GeoScope?,
    playerWorldId: Identifier
): ModifySelectionTargetError? {
    val shape = scope.geoShape
    if (canonicalScope !== scope || shape == null || shape.geoShapeType == GeoShapeType.UNKNOWN) {
        return ModifySelectionTargetError.INVALID_TARGET
    }
    return if (scope.worldId == playerWorldId) null else ModifySelectionTargetError.WRONG_WORLD
}

internal fun clearPlayerSelection(playerId: UUID): Boolean =
    ImyvmWorldGeo.pointSelectingPlayers.remove(playerId) != null

internal fun clearAllSelections() {
    ImyvmWorldGeo.pointSelectingPlayers.clear()
}

internal fun clearSelectionsReferencing(scopes: Collection<GeoScope>) {
    if (scopes.isEmpty()) return
    for ((playerId, state) in ImyvmWorldGeo.pointSelectingPlayers) {
        val target = (state.hypotheticalShape as? HypotheticalShape.ModifyExisting)?.scope ?: continue
        if (scopes.any { it === target }) {
            ImyvmWorldGeo.pointSelectingPlayers.remove(playerId, state)
        }
    }
}

internal fun onPlayerSelectionWorldChanged(player: ServerPlayer) {
    if (clearPlayerSelection(player.uuid)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.cleared.world_change")!!)
    }
}

fun onStartSelection(player: ServerPlayer, shapeType: GeoShapeType? = null): Int {
    val playerUUID = player.uuid
    if (ImyvmWorldGeo.pointSelectingPlayers[playerUUID] != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeType.name)!!)
        return 0
    }
    val state = SelectionState(hypotheticalShape = shapeType?.let { HypotheticalShape.Normal(it) }, worldId = player.level().dimension().identifier())
    if (ImyvmWorldGeo.pointSelectingPlayers.putIfAbsent(playerUUID, state) != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    if (shapeType != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.start.with_shape", shapeType.name)!!)
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.start")!!)
    }
    return 1
}

fun onStopSelection(player: ServerPlayer): Int {
    val playerUUID = player.uuid
    return if (clearPlayerSelection(playerUUID)) {
        clearSelectionDisplay(player)
        player.sendSystemMessage(Translator.tr("interaction.meta.select.stop")!!)
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        0
    }
}

fun onResetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null): Int {
    val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (state == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        return 0
    }
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeType.name)!!)
        return 0
    }
    if (!resetSelectionState(state, shapeType)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.shape.cannot_change_modify")!!)
        return 0
    }
    if (shapeType != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.reset.with_shape", shapeType.name)!!)
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.reset")!!)
    }
    return 1
}

fun onSetSelectionShape(player: ServerPlayer, shapeType: GeoShapeType): Int {
    val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (state == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        return 0
    }
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeType.name)!!)
        return 0
    }
    if (!isCreationSelection(state)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.shape.cannot_change_modify")!!)
        return 0
    }
    state.hypotheticalShape = when (val current = state.hypotheticalShape) {
        is HypotheticalShape.SubSpace -> current.copy(shapeType = shapeType)
        else -> HypotheticalShape.Normal(shapeType)
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.select.shape.success", shapeType.name)!!)
    return 1
}


fun onStartSelectionForSubSpace(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    shapeType: GeoShapeType? = null
): Int {
    val playerUUID = player.uuid
    if (ImyvmWorldGeo.pointSelectingPlayers[playerUUID] != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeType.name)!!)
        return 0
    }
    RegionDatabase.requireCanonicalScope(region, parentScope)
    if (player.level().dimension().identifier() != parentScope.worldId) {
        player.sendSystemMessage(Translator.tr("interaction.meta.subspace.error.wrong_world", parentScope.scopeName, region.name)!!)
        return 0
    }
    if (parentScope.geoShape == null) {
        player.sendSystemMessage(Translator.tr("error.subspace.outside_parent_scope", parentScope.scopeName, region.name)!!)
        return 0
    }
    val state = SelectionState(
        hypotheticalShape = HypotheticalShape.SubSpace(region.name, parentScope, shapeType),
        worldId = parentScope.worldId
    )
    if (ImyvmWorldGeo.pointSelectingPlayers.putIfAbsent(playerUUID, state) != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    if (shapeType == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.start.subspace", region.name, parentScope.scopeName)!!)
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.start.subspace.with_shape", region.name, parentScope.scopeName, shapeType.name)!!)
    }
    return 1
}

fun onStartSelectionForModify(player: ServerPlayer, scope: GeoScope): Int {
    val playerUUID = player.uuid
    if (ImyvmWorldGeo.pointSelectingPlayers[playerUUID] != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    val error = validateModifySelectionStartTarget(
        scope,
        player.level().dimension().identifier()
    )
    if (error != null) {
        sendModifySelectionTargetError(player, error)
        return 0
    }
    val state = SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(scope), worldId = scope.worldId)
    if (ImyvmWorldGeo.pointSelectingPlayers.putIfAbsent(playerUUID, state) != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        return 0
    }
    player.sendSystemMessage(buildModifyStartMessage(scope))
    return 1
}

internal fun sendModifySelectionTargetError(player: ServerPlayer, error: ModifySelectionTargetError) {
    val key = when (error) {
        ModifySelectionTargetError.INVALID_TARGET -> "interaction.meta.select.modify.invalid_target"
        ModifySelectionTargetError.WRONG_WORLD -> "interaction.meta.select.modify.wrong_world"
    }
    player.sendSystemMessage(Translator.tr(key)!!)
}
