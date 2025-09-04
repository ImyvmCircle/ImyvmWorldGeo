package com.imyvm.iwg.application

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionFactory
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun selectionModeCheck(player: ServerPlayerEntity): Boolean{
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        return false
    }

    return true
}

fun getNameAutoFillCheck(player: ServerPlayerEntity, nameArgument: String?): String? {
    return try {
        if (nameArgument == null) {
            val name = "NewRegion-${System.currentTimeMillis()}"
            player.sendMessage(Translator.tr("command.create.name_auto_filled", name))
            name
        } else if (nameArgument.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.create.name_is_digits_only"))
            null
        } else {
            nameArgument
        }
    } catch (e: IllegalArgumentException) {
        val name = "NewRegion-${System.currentTimeMillis()}"
        player.sendMessage(Translator.tr("command.create.name_auto_filled", name))
        name
    }
}

fun getNameCheck(player: ServerPlayerEntity, nameArgument: String?): String? {
    return try {
        if (nameArgument == null) {
            player.sendMessage(Translator.tr("command.create.name_invalid"))
            null
        } else if (nameArgument.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.create.name_is_digits_only"))
            null
        } else {
            nameArgument
        }
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("command.create.name_invalid"))
        null
    }
}

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
    val selectedPositions = ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]
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

    ImyvmWorldGeo.commandlySelectingPlayers.remove(player.uuid)
}
