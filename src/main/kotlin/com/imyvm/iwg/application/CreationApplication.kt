package com.imyvm.iwg.application

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionFactory
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun selectionModeCheck(player: ServerPlayerEntity): Boolean {
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        return false
    }
    return true
}

fun getRegionNameAutoFillCheck(player: ServerPlayerEntity, nameArgument: String?): String? =
    validateNameCommon(player, nameArgument, autoFill = true)

fun getRegionNameCheck(player: ServerPlayerEntity, nameArgument: String?): String? =
    validateNameCommon(player, nameArgument, autoFill = false)

fun getShapeTypeCheck(
    player: ServerPlayerEntity,
    shapeTypeName: String,
): Region.Companion.GeoShapeType? {
    val shapeType = Region.Companion.GeoShapeType.entries
        .find { it.name == shapeTypeName }
        ?: Region.Companion.GeoShapeType.UNKNOWN
    if (shapeType == Region.Companion.GeoShapeType.UNKNOWN) {
        player.sendMessage(Translator.tr("command.create.invalid_shape", shapeTypeName))
        return null
    }
    return shapeType
}

fun tryRegionCreation(
    player: ServerPlayerEntity,
    regionName: String,
    shapeType: Region.Companion.GeoShapeType,
): Result<Region, CreationError> {
    val playerUUID = player.uuid
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID]
    val biggestId = ImyvmWorldGeo.data.getRegionList().maxOfOrNull { it.numberID } ?: -1
    val newID = biggestId + 1
    return RegionFactory.createRegion(
        name = regionName,
        numberID = newID,
        selectedPositions = selectedPositions ?: mutableListOf(),
        shapeType = shapeType
    )
}

fun handleRegionCreateSuccessInternally(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region>
) = handleRegionCreateSuccessCommon(player, creationResult, notify = true)

fun handleRegionCreateSuccess(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region>
) = handleRegionCreateSuccessCommon(player, creationResult, notify = false)

fun getScopeNameCheck(
    player: ServerPlayerEntity,
    region: Region,
    scopeNameArg: String?
): String? {
    val scopeName = if (scopeNameArg == null) {
        val autoName = "NewScope-${region.name}-${System.currentTimeMillis()}"
        player.sendMessage(Translator.tr("command.scope.add.name_auto_filled", autoName))
        autoName
    } else {
        scopeNameArg
    }

    return validateNameCommon(player, scopeName, autoFill = false)
        ?.let { validateScopeUnique(player, region, it) }
}

fun tryScopeCreation(
    playerUUID: UUID,
    scopeName: String,
    shapeType: Region.Companion.GeoShapeType
): Result<Region.Companion.GeoScope, CreationError> {
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: mutableListOf()
    return RegionFactory.createScope(
        scopeName = scopeName,
        selectedPositions = selectedPositions,
        shapeType = shapeType
    )
}

fun handleScopeCreateSuccess(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region.Companion.GeoScope>,
    region: Region
) {
    val newScope = creationResult.value
    region.geometryScope.add(newScope)

    player.sendMessage(
        Translator.tr("command.scope.add.success", newScope.scopeName, region.name)
    )
    ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
}

private fun handleRegionCreateSuccessCommon(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region>,
    notify: Boolean
) {
    val newRegion = creationResult.value
    ImyvmWorldGeo.data.addRegion(newRegion)
    ImyvmWorldGeo.logger.info("Created new region '${newRegion.name}' with positions: ${newRegion.geometryScope}")

    if (notify) {
        player.sendMessage(Translator.tr("command.create.success", newRegion.name))
    }
    ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
}

private fun validateNameCommon(
    player: ServerPlayerEntity,
    nameArgument: String?,
    autoFill: Boolean
): String? {
    return try {
        if (nameArgument == null) {
            if (autoFill) {
                val name = "NewRegion-${System.currentTimeMillis()}"
                player.sendMessage(Translator.tr("command.create.name_auto_filled", name))
                name
            } else {
                player.sendMessage(Translator.tr("command.create.name_invalid"))
                null
            }
        } else if (nameArgument.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.create.name_is_digits_only"))
            null
        } else {
            nameArgument
        }
    } catch (e: IllegalArgumentException) {
        if (autoFill) {
            val name = "NewRegion-${System.currentTimeMillis()}"
            player.sendMessage(Translator.tr("command.create.name_auto_filled", name))
            name
        } else {
            player.sendMessage(Translator.tr("command.create.name_invalid"))
            null
        }
    }
}

private fun validateScopeUnique(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String
): String? {
    if (region.geometryScope.any { it.scopeName.equals(scopeName, ignoreCase = true) }) {
        player.sendMessage(Translator.tr("command.scope.add.duplicate_scope_name"))
        return null
    }
    return scopeName
}