package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.*
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.application.selection.getEffectiveShapeType
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.application.region.generateNewRegionId
import com.imyvm.iwg.application.region.RegionIdCapacityExceededException
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.ScopeIdCapacityExceededException
import com.imyvm.iwg.domain.component.SelectionState
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun onRegionCreation(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = false,
    idMark: Int
): Int {
    val region = onTryingRegionCreationWithReturn(player, regionNameArg, shapeTypeName, isApi, idMark)
    return if (region != null) 1 else 0
}

fun onTryingRegionCreationWithReturn(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = true,
    idMark: Int
): Region? {
    val selectionState = getCreationSelectionOrNotify(player) ?: return null

    val regionName = validateNameCommon(
        player,
        regionNameArg,
        type = NameType.REGION,
        autoFill = !isApi
    ) ?: return null

    val shapeType = getShapeTypeCheck(player, selectionState, shapeTypeName) ?: return null

    val creationResult = try {
        tryRegionCreation(player, regionName, shapeType, idMark, selectionState.points)
    } catch (_: RegionIdCapacityExceededException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.error.id_capacity")!!)
        return null
    }
    return when (creationResult) {
        is Result.Ok -> {
            val saved = handleRegionCreateSuccess(player, creationResult, notify = !isApi)
            creationResult.value.takeIf { saved }
        }
        is Result.Err -> {
            errorMessage(creationResult.error, shapeType).forEach { player.sendSystemMessage(it) }
            null
        }
    }
}

fun onTryingRegionCreationWithShape(
    player: ServerPlayer,
    regionName: String,
    idMark: Int,
    shape: GeoShape
): Region? {
    val name = validateNameCommon(player, regionName, type = NameType.REGION, autoFill = false) ?: return null

    val creationResult = try {
        tryRegionCreationFromShape(player, name, idMark, shape)
    } catch (_: RegionIdCapacityExceededException) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.error.id_capacity")!!)
        return null
    }
    return when (creationResult) {
        is Result.Ok -> {
            val saved = handleRegionCreateSuccess(player, creationResult, notify = true)
            creationResult.value.takeIf { saved }
        }
        is Result.Err -> {
            errorMessage(creationResult.error, shape.geoShapeType).forEach { player.sendSystemMessage(it) }
            null
        }
    }
}

fun onTryingScopeCreationWithShape(
    player: ServerPlayer,
    region: Region,
    scopeName: String,
    shape: GeoShape
): GeoScope? {
    RegionDatabase.requireCanonicalRegion(region)

    val name = validateNameCommon(player, scopeName, type = NameType.SCOPE, autoFill = false, regionForScope = region) ?: return null

    return when (val result = RegionFactory.createScopeFromShape(name, player, shape)) {
        is Result.Ok -> {
            val newScope = result.value
            try {
                newScope.assignScopeId(RegionDatabase.nextScopeIdForNewScope(region))
            } catch (_: ScopeIdCapacityExceededException) {
                player.sendSystemMessage(Translator.tr("interaction.meta.scope.create.error.id_capacity")!!)
                return null
            }
            region.addScopeFromOwner(newScope)
            if (!saveRegionData(player)) {
                region.removeScopeFromOwner(newScope)
                return null
            }
            player.sendSystemMessage(
                Translator.tr("interaction.meta.scope.add.success", newScope.scopeName, region.name)!!
            )
            newScope
        }
        is Result.Err -> {
            errorMessage(result.error, shape.geoShapeType).forEach { player.sendSystemMessage(it) }
            null
        }
    }
}

fun onScopeCreation(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = false
): Int {
    val resultPair = onTryingScopeCreationWithReturn(player, region, scopeNameArg, shapeTypeName, isApi)
    return if (resultPair != null) 1 else 0
}

fun onTryingScopeCreationWithReturn (
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = true
): Pair<Region, GeoScope>? {
    RegionDatabase.requireCanonicalRegion(region)

    val selectionState = getCreationSelectionOrNotify(player) ?: return null
    val shapeType = getShapeTypeCheck(player, selectionState, shapeTypeName) ?: return null

    val scopeName = validateNameCommon(
        player,
        scopeNameArg,
        type = NameType.SCOPE,
        autoFill = !isApi,
        regionForScope = region
    ) ?: return null

    return when (val creationResult = tryScopeCreation(player, scopeName, shapeType, selectionState.points)) {
        is Result.Ok -> {
            val saved = handleScopeCreateSuccess(player, creationResult, region, notify = !isApi)
            Pair(region, creationResult.value).takeIf { saved }
        }
        is Result.Err -> {
            errorMessage(creationResult.error, shapeType).forEach { player.sendSystemMessage(it) }
            null
        }
    }
}

private fun getCreationSelectionOrNotify(player: ServerPlayer): SelectionState? {
    val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]
    if (state == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        return null
    }
    if (!isCreationSelection(state)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.create_mode_required")!!)
        return null
    }
    return state
}

private fun getShapeTypeCheck(
    player: ServerPlayer,
    selectionState: SelectionState,
    shapeTypeName: String?,
): GeoShapeType? {
    if (shapeTypeName.isNullOrEmpty()) {
        return selectionState.getEffectiveShapeType()
    }
    val shapeType = GeoShapeType.entries
        .find { it.name == shapeTypeName }
        ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeName)!!)
        return null
    }
    return shapeType
}

private fun tryRegionCreation(
    player: ServerPlayer,
    regionName: String,
    shapeType: GeoShapeType,
    idMark: Int,
    selectedPositions: MutableList<BlockPos>
): Result<Region, CreationError> {
    val newID = generateNewRegionId(idMark)
    val regionResult = RegionFactory.createRegion(
        name = regionName,
        numberID = newID,
        playerExecutor = player,
        selectedPositions = selectedPositions,
        shapeType = shapeType
    )
    if (regionResult is Result.Ok) {
        val mainScope = regionResult.value.scopes.firstOrNull()
        check(mainScope != null && mainScope.assignedScopeIdOrNull != null)
    }
    return regionResult
}

private fun tryRegionCreationFromShape(
    player: ServerPlayer,
    regionName: String,
    idMark: Int,
    shape: GeoShape
): Result<Region, CreationError> {
    val newID = generateNewRegionId(idMark)
    return RegionFactory.createRegionFromShape(regionName, newID, player, shape)
}

private fun tryScopeCreation(
    player: ServerPlayer,
    scopeName: String,
    shapeType: GeoShapeType,
    selectedPositions: MutableList<BlockPos>
): Result<GeoScope, CreationError> {
    return RegionFactory.createScopeForPlayer(
        scopeName = scopeName,
        playerExecutor = player,
        selectedPositions = selectedPositions,
        shapeType = shapeType
    )
}

private fun handleRegionCreateSuccess(
    player: ServerPlayer,
    creationResult: Result.Ok<Region>,
    notify: Boolean
): Boolean {
    val newRegion = creationResult.value
    RegionDatabase.addRegion(newRegion)
    if (!saveRegionData(player)) {
        RegionDatabase.removeRegion(newRegion)
        return false
    }

    if (notify) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.success", newRegion.name)!!)
    }
    clearSelectionDisplay(player)
    clearPlayerSelection(player.uuid)
    return true
}

private fun handleScopeCreateSuccess(
    player: ServerPlayer,
    creationResult: Result.Ok<GeoScope>,
    region: Region,
    notify: Boolean
): Boolean {
    val newScope = creationResult.value
    if (newScope.assignedScopeIdOrNull == null) {
        try {
            newScope.assignScopeId(RegionDatabase.nextScopeIdForNewScope(region))
        } catch (_: ScopeIdCapacityExceededException) {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.create.error.id_capacity")!!)
            return false
        }
    }
    region.addScopeFromOwner(newScope)
    if (!saveRegionData(player)) {
        region.removeScopeFromOwner(newScope)
        return false
    }

    if (notify) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.scope.add.success", newScope.scopeName, region.name)!!
        )
    }
    clearSelectionDisplay(player)
    clearPlayerSelection(player.uuid)
    return true
}

private fun validateNameCommon(
    player: ServerPlayer,
    nameArgument: String?,
    type: NameType,
    autoFill: Boolean = false,
    regionForScope: Region? = null
): String? {
    val name = when {
        nameArgument == null -> {
            if (autoFill) {
                generateAndNotifyAutoName(player, type, regionForScope)
            } else {
                NameValidationMessages.sendNameRequired(player, type)
                return null
            }
        }
        else -> nameArgument
    }.trim()

    if (!checkNameEmpty(name, player)) return null
    if (!checkNameFormat(name, player)) return null
    when (type) {
        NameType.REGION -> if (!checkRegionNameUnique(newName = name, player = player)) return null
        NameType.SCOPE -> if (regionForScope != null && !checkScopeUnique(name, regionForScope, player)) return null
    }

    return name
}

private fun generateAndNotifyAutoName(
    player: ServerPlayer,
    type: NameType,
    region: Region?
): String {
    val name = when (type) {
        NameType.REGION -> CreationNameGenerator.generateRegionName()
        NameType.SCOPE -> CreationNameGenerator.generateScopeName(region)
    }
    NameValidationMessages.sendAutoFilled(player, type, name)
    return name
}

fun checkScopeUnique(scopeName: String, region: Region, player: ServerPlayer): Boolean {
    if (region.scopes.any { it.scopeName.equals(scopeName, ignoreCase = true) }) {
        NameValidationMessages.sendDuplicateScope(player)
        return false
    }
    return true
}
