package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.application.interaction.helper.errorMessage
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun onRegionCreation(
    player: ServerPlayerEntity,
    regionNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = false
): Int {
    val region = onTryingRegionCreationWithReturn(player, regionNameArg, shapeTypeName, isApi)
    return if (region != null) 1 else 0
}

fun onTryingRegionCreationWithReturn(
    player: ServerPlayerEntity,
    regionNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = true
): Region? {
    if (!selectionModeCheck(player)) return null

    val regionName = validateNameCommon(
        player,
        regionNameArg,
        type = NameType.REGION,
        autoFill = !isApi
    ) ?: return null

    val shapeType = getShapeTypeCheck(player, shapeTypeName) ?: return null

    return when (val creationResult = tryRegionCreation(player, regionName, shapeType)) {
        is Result.Ok -> {
            if (isApi) handleRegionCreateSuccess(player, creationResult, notify = false)
            else handleRegionCreateSuccess(player, creationResult, notify = true)
            creationResult.value
        }
        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(errorMsg)
            null
        }
    }
}

fun onScopeCreation(
    player: ServerPlayerEntity,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = false
): Int {
    val resultPair = onTryingScopeCreationWithReturn(player, region, scopeNameArg, shapeTypeName, isApi)
    return if (resultPair != null) 1 else 0
}

fun onTryingScopeCreationWithReturn (
    player: ServerPlayerEntity,
    region: Region,
    scopeNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = true
): Pair<Region, Region.Companion.GeoScope>? {
    val playerUUID = player.uuid
    if (!selectionModeCheck(player)) return null
    val shapeType = getShapeTypeCheck(player, shapeTypeName) ?: return null

    val scopeName = validateNameCommon(
        player,
        scopeNameArg,
        type = NameType.SCOPE,
        autoFill = !isApi,
        regionForScope = region
    ) ?: return null

    return when (val creationResult = tryScopeCreation(playerUUID, scopeName, shapeType)) {
        is Result.Ok -> {
            if (isApi)
                handleScopeCreateSuccess(player, creationResult, region, notify = false)
            else
                handleScopeCreateSuccess(player, creationResult, region, notify = true)
            Pair(region, creationResult.value)
        }
        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(errorMsg)
            null
        }
    }
}

private fun selectionModeCheck(player: ServerPlayerEntity): Boolean {
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("interaction.meta.select.not_in_mode"))
        return false
    }
    return true
}

private fun getShapeTypeCheck(
    player: ServerPlayerEntity,
    shapeTypeName: String,
): Region.Companion.GeoShapeType? {
    val shapeType = Region.Companion.GeoShapeType.entries
        .find { it.name == shapeTypeName }
        ?: Region.Companion.GeoShapeType.UNKNOWN
    if (shapeType == Region.Companion.GeoShapeType.UNKNOWN) {
        player.sendMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeName))
        return null
    }
    return shapeType
}

private fun tryRegionCreation(
    player: ServerPlayerEntity,
    regionName: String,
    shapeType: Region.Companion.GeoShapeType,
): Result<Region, CreationError> {
    val playerUUID = player.uuid
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID]
    val biggestId = RegionDatabase.getRegionList().maxOfOrNull { it.numberID } ?: -1
    val newID = biggestId + 1
    return RegionFactory.createRegion(
        name = regionName,
        numberID = newID,
        selectedPositions = selectedPositions ?: mutableListOf(),
        shapeType = shapeType
    )
}

private fun tryScopeCreation(
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

private fun handleRegionCreateSuccess(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region>,
    notify: Boolean
) {
    val newRegion = creationResult.value
    RegionDatabase.addRegion(newRegion)

    if (notify) {
        player.sendMessage(Translator.tr("interaction.meta.create.success", newRegion.name))
    }
    ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
}

private fun handleScopeCreateSuccess(
    player: ServerPlayerEntity,
    creationResult: Result.Ok<Region.Companion.GeoScope>,
    region: Region,
    notify: Boolean
) {
    val newScope = creationResult.value
    region.geometryScope.add(newScope)

    if (notify) {
        player.sendMessage(
            Translator.tr("interaction.meta.scope.add.success", newScope.scopeName, region.name)
        )
    }
    ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
}

private fun validateNameCommon(
    player: ServerPlayerEntity,
    nameArgument: String?,
    type: NameType,
    autoFill: Boolean = false,
    regionForScope: Region? = null
): String? {
    return try {
        val finalName = when {
            nameArgument == null -> {
                if (autoFill) {
                    val name = when (type) {
                        NameType.REGION -> "NewRegion-${System.currentTimeMillis()}"
                        NameType.SCOPE -> {
                            val regionName = regionForScope?.name ?: "UnknownRegion"
                            "$regionName-NewScope-${System.currentTimeMillis()}"
                        }
                    }
                    val msgKey = when (type) {
                        NameType.REGION -> "interaction.meta.name_auto_filled"
                        NameType.SCOPE -> "interaction.meta.add.name_auto_filled"
                    }
                    player.sendMessage(Translator.tr(msgKey, name))
                    name
                } else {
                    val msgKey = when (type) {
                        NameType.REGION -> "interaction.meta.create.name_invalid"
                        NameType.SCOPE -> "interaction.meta.scope.add.name_invalid"
                    }
                    player.sendMessage(Translator.tr(msgKey))
                    return null
                }
            }
            nameArgument.matches("\\d+".toRegex()) -> {
                val msgKey = when (type) {
                    NameType.REGION -> "interaction.meta.create.name_is_digits_only"
                    NameType.SCOPE -> "interaction.meta.scope.add.name_is_digits_only"
                }
                player.sendMessage(Translator.tr(msgKey))
                return null
            }
            else -> nameArgument
        }

        if (type == NameType.SCOPE) {
            regionForScope?.let { validateScopeUnique(player, it, finalName) }
        } else {
            finalName
        }
    } catch (e: IllegalArgumentException) {
        val msgKey = when (type) {
            NameType.REGION -> if (autoFill) "interaction.meta.create.name_auto_filled" else "interaction.meta.create.name_invalid"
            NameType.SCOPE -> if (autoFill) "interaction.meta.scope.add.name_auto_filled" else "interaction.meta.scope.add.name_invalid"
        }
        val name = when (type) {
            NameType.REGION -> "NewRegion-${System.currentTimeMillis()}"
            NameType.SCOPE -> {
                val regionName = regionForScope?.name ?: "UnknownRegion"
                "NewScope-$regionName-${System.currentTimeMillis()}"
            }
        }
        player.sendMessage(Translator.tr(msgKey, name))
        if (autoFill) name else null
    }
}

private fun validateScopeUnique(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String
): String? {
    if (region.geometryScope.any { it.scopeName.equals(scopeName, ignoreCase = true) }) {
        player.sendMessage(Translator.tr("interaction.meta.scope.add.duplicate_scope_name"))
        return null
    }
    return scopeName
}

private enum class NameType { REGION, SCOPE }