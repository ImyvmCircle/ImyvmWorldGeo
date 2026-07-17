package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.CreationNameGenerator
import com.imyvm.iwg.application.interaction.helper.NameValidationMessages
import com.imyvm.iwg.application.interaction.helper.checkNameEmpty
import com.imyvm.iwg.application.interaction.helper.checkNameFormat
import com.imyvm.iwg.application.interaction.helper.checkRegionNameUnique
import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.RegionIdCapacityExceededException
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.region.generateNewRegionId
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.application.selection.getEffectiveShapeType
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.ScopeIdCapacityExceededException
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

internal enum class CreationSelectionError {
    NOT_IN_SELECTION_MODE,
    CREATION_MODE_REQUIRED,
    UNSUPPORTED_SHAPE
}

internal class CreationSelection private constructor(
    val points: List<BlockPos>,
    val shapeType: GeoShapeType
) {
    companion object {
        fun create(points: List<BlockPos>, shapeType: GeoShapeType): Result<CreationSelection, CreationSelectionError> {
            if (shapeType == GeoShapeType.UNKNOWN) {
                return Result.Err(CreationSelectionError.UNSUPPORTED_SHAPE)
            }
            return Result.Ok(CreationSelection(points.toList(), shapeType))
        }
    }
}

internal fun resolveCreationSelection(
    state: SelectionState?
): Result<CreationSelection, CreationSelectionError> {
    if (state == null) return Result.Err(CreationSelectionError.NOT_IN_SELECTION_MODE)
    if (state.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        return Result.Err(CreationSelectionError.CREATION_MODE_REQUIRED)
    }
    return CreationSelection.create(state.points, state.getEffectiveShapeType())
}

internal fun resolveCreationSelection(
    state: SelectionState?,
    explicitShape: GeoShapeType
): Result<CreationSelection, CreationSelectionError> {
    if (state == null) return Result.Err(CreationSelectionError.NOT_IN_SELECTION_MODE)
    if (state.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        return Result.Err(CreationSelectionError.CREATION_MODE_REQUIRED)
    }
    return CreationSelection.create(state.points, explicitShape)
}

internal fun createRegionFromSelection(
    player: ServerPlayer,
    name: String,
    idMark: Int
): Region? {
    val regionName = validateRegionCreationName(player, name) ?: return null
    val selection = resolveCurrentCreationSelection(player) ?: return null
    return createRegionFromSelection(player, regionName, idMark, selection)
}

internal fun createScopeFromSelection(
    player: ServerPlayer,
    region: Region,
    name: String
): GeoScope? {
    RegionDatabase.requireCanonicalRegion(region)
    val scopeName = validateScopeCreationName(player, region, name) ?: return null
    val selection = resolveCurrentCreationSelection(player) ?: return null
    return createScopeFromSelection(player, region, scopeName, selection)
}

@Deprecated("Use createRegionFromSelection with a non-null name")
fun onRegionCreation(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean = true,
    notifyPlayer: Boolean = true,
    idMark: Int
): Int = if (
    createRegionCompatibility(
        player,
        regionNameArg,
        shapeTypeName,
        autoFillName,
        notifyPlayer,
        idMark
    ) != null
) 1 else 0

@Deprecated("Use createRegionFromSelection with a non-null name")
fun onTryingRegionCreationWithReturn(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean = false,
    notifyPlayer: Boolean = false,
    idMark: Int
): Region? = createRegionCompatibility(
    player,
    regionNameArg,
    shapeTypeName,
    autoFillName,
    notifyPlayer,
    idMark
)

@Deprecated("Use createScopeFromSelection with a non-null name")
fun onScopeCreation(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean = true,
    notifyPlayer: Boolean = true
): Int = if (
    createScopeCompatibility(
        player,
        region,
        scopeNameArg,
        shapeTypeName,
        autoFillName,
        notifyPlayer
    ) != null
) 1 else 0

@Deprecated("Use createScopeFromSelection with a non-null name")
fun onTryingScopeCreationWithReturn(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean = false,
    notifyPlayer: Boolean = false
): Pair<Region, GeoScope>? = createScopeCompatibility(
    player,
    region,
    scopeNameArg,
    shapeTypeName,
    autoFillName,
    notifyPlayer
)?.let { region to it }

@Deprecated("Use createRegionFromSelection with a non-null name")
@JvmName("onRegionCreation")
fun onRegionCreationLegacy(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = false,
    idMark: Int
): Int = if (
    createRegionCompatibility(
        player,
        regionNameArg,
        shapeTypeName,
        autoFillName = !isApi,
        notifyPlayer = !isApi,
        idMark
    ) != null
) 1 else 0

@Deprecated("Use createRegionFromSelection with a non-null name")
@JvmName("onTryingRegionCreationWithReturn")
fun onTryingRegionCreationWithReturnLegacy(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = true,
    idMark: Int
): Region? = createRegionCompatibility(
    player,
    regionNameArg,
    shapeTypeName,
    autoFillName = !isApi,
    notifyPlayer = !isApi,
    idMark
)

@Deprecated("Use createScopeFromSelection with a non-null name")
@JvmName("onScopeCreation")
fun onScopeCreationLegacy(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = false
): Int = if (
    createScopeCompatibility(
        player,
        region,
        scopeNameArg,
        shapeTypeName,
        autoFillName = !isApi,
        notifyPlayer = !isApi
    ) != null
) 1 else 0

@Deprecated("Use createScopeFromSelection with a non-null name")
@JvmName("onTryingScopeCreationWithReturn")
fun onTryingScopeCreationWithReturnLegacy(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    isApi: Boolean = true
): Pair<Region, GeoScope>? = createScopeCompatibility(
    player,
    region,
    scopeNameArg,
    shapeTypeName,
    autoFillName = !isApi,
    notifyPlayer = !isApi
)?.let { region to it }

private fun createRegionCompatibility(
    player: ServerPlayer,
    regionNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean,
    notifyPlayer: Boolean,
    idMark: Int
): Region? {
    val selection = resolveCompatibilitySelection(player, shapeTypeName) ?: return null
    val candidateName = resolveCompatibilityRegionName(player, regionNameArg, autoFillName) ?: return null
    val regionName = validateRegionCreationName(player, candidateName) ?: return null
    val region = createRegionFromSelection(player, regionName, idMark, selection) ?: return null
    if (notifyPlayer) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.create.success", region.name)))
    }
    return region
}

private fun createScopeCompatibility(
    player: ServerPlayer,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String?,
    autoFillName: Boolean,
    notifyPlayer: Boolean
): GeoScope? {
    RegionDatabase.requireCanonicalRegion(region)
    val selection = resolveCompatibilitySelection(player, shapeTypeName) ?: return null
    val candidateName = resolveCompatibilityScopeName(player, region, scopeNameArg, autoFillName) ?: return null
    val scopeName = validateScopeCreationName(player, region, candidateName) ?: return null
    val scope = createScopeFromSelection(player, region, scopeName, selection) ?: return null
    if (notifyPlayer) {
        player.sendSystemMessage(
            requireNotNull(Translator.tr("interaction.meta.scope.add.success", scope.scopeName, region.name))
        )
    }
    return scope
}

private fun resolveCurrentCreationSelection(player: ServerPlayer): CreationSelection? =
    notifySelectionError(player, resolveCreationSelection(ImyvmWorldGeo.pointSelectingPlayers[player.uuid]))

private fun resolveCurrentCreationSelection(
    player: ServerPlayer,
    explicitShape: GeoShapeType
): CreationSelection? = notifySelectionError(
    player,
    resolveCreationSelection(ImyvmWorldGeo.pointSelectingPlayers[player.uuid], explicitShape)
)

private fun notifySelectionError(
    player: ServerPlayer,
    result: Result<CreationSelection, CreationSelectionError>
): CreationSelection? = when (result) {
    is Result.Ok -> result.value
    is Result.Err -> {
        val key = when (result.error) {
            CreationSelectionError.NOT_IN_SELECTION_MODE -> "interaction.meta.select.not_in_mode"
            CreationSelectionError.CREATION_MODE_REQUIRED -> "interaction.meta.select.create_mode_required"
            CreationSelectionError.UNSUPPORTED_SHAPE -> "interaction.meta.create.invalid_shape"
        }
        val message = if (result.error == CreationSelectionError.UNSUPPORTED_SHAPE) {
            Translator.tr(key, GeoShapeType.UNKNOWN.name)
        } else {
            Translator.tr(key)
        }
        player.sendSystemMessage(requireNotNull(message))
        null
    }
}

private fun resolveCompatibilitySelection(
    player: ServerPlayer,
    shapeTypeName: String?
): CreationSelection? {
    if (shapeTypeName.isNullOrEmpty()) return resolveCurrentCreationSelection(player)
    val shapeType = GeoShapeType.entries.find { it.name == shapeTypeName } ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(
            requireNotNull(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeName))
        )
        return null
    }
    return resolveCurrentCreationSelection(player, shapeType)
}

private fun resolveCompatibilityRegionName(
    player: ServerPlayer,
    nameArgument: String?,
    autoFill: Boolean
): String? {
    if (nameArgument != null) return nameArgument
    if (!autoFill) {
        NameValidationMessages.sendRegionNameRequired(player)
        return null
    }
    val name = CreationNameGenerator.generateRegionName()
    NameValidationMessages.sendRegionAutoFilled(player, name)
    return name
}

private fun resolveCompatibilityScopeName(
    player: ServerPlayer,
    region: Region,
    nameArgument: String?,
    autoFill: Boolean
): String? {
    if (nameArgument != null) return nameArgument
    if (!autoFill) {
        NameValidationMessages.sendScopeNameRequired(player)
        return null
    }
    val name = CreationNameGenerator.generateScopeName(region)
    NameValidationMessages.sendScopeAutoFilled(player, name)
    return name
}

private fun createRegionFromSelection(
    player: ServerPlayer,
    regionName: String,
    idMark: Int,
    selection: CreationSelection
): Region? {
    val creationResult = try {
        val newId = generateNewRegionId(idMark)
        RegionFactory.createRegion(
            name = regionName,
            numberID = newId,
            playerExecutor = player,
            selectedPositions = selection.points,
            shapeType = selection.shapeType
        )
    } catch (_: RegionIdCapacityExceededException) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.create.error.id_capacity")))
        return null
    }
    return when (creationResult) {
        is Result.Ok -> {
            val region = creationResult.value
            val mainScope = region.scopes.firstOrNull()
            check(mainScope != null && mainScope.assignedScopeIdOrNull != null)
            if (!persistCreatedRegion(region) { saveRegionData(player) }) return null
            clearSuccessfulCreationSelection(player)
            region
        }
        is Result.Err -> {
            errorMessage(creationResult.error, selection.shapeType).forEach(player::sendSystemMessage)
            null
        }
    }
}

private fun createScopeFromSelection(
    player: ServerPlayer,
    region: Region,
    scopeName: String,
    selection: CreationSelection
): GeoScope? {
    return when (
        val creationResult = RegionFactory.createScopeForPlayer(
            scopeName = scopeName,
            playerExecutor = player,
            selectedPositions = selection.points,
            shapeType = selection.shapeType
        )
    ) {
        is Result.Ok -> {
            val scope = creationResult.value
            if (scope.assignedScopeIdOrNull == null) {
                try {
                    scope.assignScopeId(RegionDatabase.nextScopeIdForNewScope(region))
                } catch (_: ScopeIdCapacityExceededException) {
                    player.sendSystemMessage(
                        requireNotNull(Translator.tr("interaction.meta.scope.create.error.id_capacity"))
                    )
                    return null
                }
            }
            if (!persistCreatedScope(region, scope) { saveRegionData(player) }) return null
            clearSuccessfulCreationSelection(player)
            scope
        }
        is Result.Err -> {
            errorMessage(creationResult.error, selection.shapeType).forEach(player::sendSystemMessage)
            null
        }
    }
}

private fun clearSuccessfulCreationSelection(player: ServerPlayer) {
    clearSelectionDisplay(player)
    clearPlayerSelection(player.uuid)
}

internal fun persistCreatedRegion(region: Region, save: () -> Boolean): Boolean {
    val rollback = RegionDatabase.insertRegionReversibly(region)
    if (!save()) {
        rollback()
        return false
    }
    return true
}

internal fun persistCreatedScope(region: Region, scope: GeoScope, save: () -> Boolean): Boolean {
    RegionDatabase.requireCanonicalRegion(region)
    scope.requireAssignedScopeId()
    region.addOwnedScope(scope)
    if (!save()) {
        region.removeOwnedScope(scope)
        return false
    }
    return true
}

private fun validateRegionCreationName(player: ServerPlayer, nameArgument: String): String? {
    val name = validateCreationName(player, nameArgument) ?: return null
    return name.takeIf { checkRegionNameUnique(newName = it, player = player) }
}

private fun validateScopeCreationName(
    player: ServerPlayer,
    region: Region,
    nameArgument: String
): String? {
    val name = validateCreationName(player, nameArgument) ?: return null
    return name.takeIf { checkScopeUnique(it, region, player) }
}

private fun validateCreationName(player: ServerPlayer, nameArgument: String): String? {
    val name = nameArgument.trim()
    if (!checkNameEmpty(name, player)) return null
    if (!checkNameFormat(name, player)) return null
    return name
}

fun checkScopeUnique(scopeName: String, region: Region, player: ServerPlayer): Boolean {
    if (region.scopes.any { it.scopeName.equals(scopeName, ignoreCase = true) }) {
        NameValidationMessages.sendDuplicateScope(player)
        return false
    }
    return true
}
