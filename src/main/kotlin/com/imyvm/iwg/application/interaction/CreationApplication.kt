package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.interaction.helper.*
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.application.region.RegionFactory
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.application.region.generateNewRegionId
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionCreation(
    player: ServerPlayerEntity,
    regionNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = false,
    idMark: Int
): Int {
    val region = onTryingRegionCreationWithReturn(player, regionNameArg, shapeTypeName, isApi, idMark)
    return if (region != null) 1 else 0
}

fun onTryingRegionCreationWithReturn(
    player: ServerPlayerEntity,
    regionNameArg: String?,
    shapeTypeName: String,
    isApi: Boolean = true,
    idMark: Int
): Region? {
    if (!selectionModeCheck(player)) return null

    val regionName = validateNameCommon(
        player,
        regionNameArg,
        type = NameType.REGION,
        autoFill = !isApi
    ) ?: return null

    val shapeType = getShapeTypeCheck(player, shapeTypeName) ?: return null

    return when (val creationResult = tryRegionCreation(player, regionName, shapeType, idMark)) {
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
): Pair<Region, GeoScope>? {
    if (!selectionModeCheck(player)) return null
    val shapeType = getShapeTypeCheck(player, shapeTypeName) ?: return null

    val scopeName = validateNameCommon(
        player,
        scopeNameArg,
        type = NameType.SCOPE,
        autoFill = !isApi,
        regionForScope = region
    ) ?: return null

    return when (val creationResult = tryScopeCreation(player, scopeName, shapeType)) {
        is Result.Ok -> {
            if (isApi) handleScopeCreateSuccess(player, creationResult, region, notify = false)
            else handleScopeCreateSuccess(player, creationResult, region, notify = true)
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
): GeoShapeType? {
    val shapeType = GeoShapeType.entries
        .find { it.name == shapeTypeName }
        ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeName))
        return null
    }
    return shapeType
}

private fun tryRegionCreation(
    player: ServerPlayerEntity,
    regionName: String,
    shapeType: GeoShapeType,
    idMark: Int
): Result<Region, CreationError> {
    val playerUUID = player.uuid
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID]
    val newID = generateNewRegionId(idMark)
    return RegionFactory.createRegion(
        name = regionName,
        numberID = newID,
        playerExecutor = player,
        selectedPositions = selectedPositions ?: mutableListOf(),
        shapeType = shapeType
    )
}

private fun tryScopeCreation(
    player: ServerPlayerEntity,
    scopeName: String,
    shapeType: GeoShapeType
): Result<GeoScope, CreationError> {
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[player.uuid] ?: mutableListOf()
    return RegionFactory.createScope(
        scopeName = scopeName,
        playerExecutor = player,
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
    creationResult: Result.Ok<GeoScope>,
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
    }

    if (!checkNameEmpty(name, player)) return null
    if (!checkNameDigit(name, player)) return null
    if (!checkNameRepeat(newName = name, player = player)) return null
    if (type == NameType.SCOPE && regionForScope != null) {
        if (!checkScopeUnique(name, regionForScope, player)) return null
    }

    return name
}

private fun generateAndNotifyAutoName(
    player: ServerPlayerEntity,
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

fun checkScopeUnique(scopeName: String, region: Region, player: ServerPlayerEntity): Boolean {
    if (region.geometryScope.any { it.scopeName.equals(scopeName, ignoreCase = true) }) {
        NameValidationMessages.sendDuplicateScope(player)
        return false
    }
    return true
}