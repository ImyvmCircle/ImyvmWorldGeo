package com.imyvm.iwg.inter.register

import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.RegionNotFoundException
import com.imyvm.iwg.application.comapp.*
import com.imyvm.iwg.util.command.getOptionalArgument
import com.imyvm.iwg.util.ui.Translator
import com.imyvm.iwg.util.ui.errorMessage
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.CompletableFuture

private val SHAPE_TYPE_SUGGESTION_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    Region.Companion.GeoShapeType.entries
        .filter { it != Region.Companion.GeoShapeType.UNKNOWN }
        .forEach { builder.suggest(it.name.lowercase(Locale.getDefault())) }
    CompletableFuture.completedFuture(builder.build())
}

//TODO("MERGE_REGION_IDENTIFIER")
//TODO("PROVIDER_REGION_IDENTIFIER")
//TODO("PROVIDER_SCOPE_IDENTIFIER")
//TODO("PROVIDER_PLAYER_LIST_ONLINE")
//TODO("PROVIDER_SETTING_KEY")
fun register(dispatcher: CommandDispatcher<ServerCommandSource>, registryAccess: CommandRegistryAccess) {
    dispatcher.register(
        literal("imyvm-world-geo")
            .then(
                literal("help")
                    .executes { runHelp(it) }
            )
            .then(
                literal("select")
                    .then(
                        literal("start")
                            .executes { runStartSelect(it) }
                    )
                    .then(
                        literal("stop")
                            .executes { runStopSelect(it) }
                    )
                    .then(
                        literal("reset")
                            .executes { runResetSelect(it) }
                    )
            )
            .then(
                literal("create")
                    .then(
                        argument("shapeType", StringArgumentType.word())
                            .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                            .executes { runCreateRegion(it) }
                            .then(
                                argument("name", StringArgumentType.word())
                                    .executes { runCreateRegion(it) }
                            )
                    )
            )
            .then(
                literal("delete")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .executes { runDeleteRegion(it) }
                    )
            )
            .then(
                literal("rename")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .then(argument("newName", StringArgumentType.string())
                                .executes { runRenameRegion(it) }
                            )
                    )
            )
            .then(
                literal("addscope")
                    .then(
                        argument("shapeType", StringArgumentType.word())
                            .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                            .then(
                                argument("id", IntegerArgumentType.integer())
                                    .executes { runAddScopeById(it) }
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .executes { runAddScopeById(it) }
                                    )
                            )
                            .then(
                                argument("name", StringArgumentType.string())
                                    .executes { runAddScopeByName(it) }
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .executes { runAddScopeByName(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("deletescope")
                    .then(
                        argument("id", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runDeleteScopeById(it) }
                            )
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runDeleteScopeByName(it) }
                            )
                    )
            )
            .then(
                literal("modifyscope")
                    .then(
                        argument("id", IntegerArgumentType.integer())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runModifyScopeById(it) }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { runRenameScopeById(it) }
                                    )
                            )
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runModifyScopeByName(it) }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { runRenameScopeByName(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("setting")
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .then(
                                        argument("settingType", StringArgumentType.string())
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .then(
                                                        argument("value", StringArgumentType.string())
                                                            .then(
                                                                argument("isPersonal", BoolArgumentType.bool())
                                                                    .executes{ runAddSettingRegion(it) }
                                                                    .then(
                                                                        argument("playerName", StringArgumentType.string())
                                                                            .executes{ runAddSettingRegion(it) }
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("remove")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .then(
                                        argument("settingType", StringArgumentType.word())
                                            .then(
                                                argument("key", StringArgumentType.word())
                                                    .then(
                                                        argument("isPersonal", BoolArgumentType.bool())
                                                            .executes{ runDeleteSettingRegion(it) }
                                                            .then(
                                                                argument("playerName", StringArgumentType.string())
                                                                    .executes{ runDeleteSettingRegion(it) }
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("settingscope")
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .then(
                                                argument("settingType", StringArgumentType.string())
                                                    .then(
                                                        argument("key", StringArgumentType.string())
                                                            .then(
                                                                argument("value", StringArgumentType.string())
                                                                    .then(
                                                                        argument("isPersonal", BoolArgumentType.bool())
                                                                            .executes{ runAddSettingScope(it) }
                                                                            .then(
                                                                                argument("playerName", StringArgumentType.string())
                                                                                    .executes{ runAddSettingScope(it) }
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("remove")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .then(
                                        argument("scopeName", StringArgumentType.word())
                                            .then(
                                                argument("settingType", StringArgumentType.word())
                                                    .then(
                                                        argument("key", StringArgumentType.word())
                                                            .then(
                                                                argument("isPersonal", BoolArgumentType.bool())
                                                                    .executes{ runDeleteSettingScope(it) }
                                                                    .then(
                                                                        argument("playerName", StringArgumentType.string())
                                                                            .executes{ runDeleteSettingScope(it) }
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("query")
                    .then(
                        argument("id", IntegerArgumentType.integer())
                            .executes{ runQueryRegionById(it) }
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .executes{ runQueryRegionByName(it) }
                    )
            )
            .then(
                literal("list")
                    .executes { runListRegions(it) }
            )
            .then(
                literal("toggle")
                    .executes{ runChangeDisplayMode(it) }
            )
    )
}

fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    player.sendMessage(Translator.tr("command.help"))
    return 1
}

private fun runStartSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return startSelection(player)
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return stopSelection(player)
}

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return resetSelection(player)
}

private fun runCreateRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    if (!selectionModeCheck(player)) return 0
    val regionName = getRegionNameAutoFillCheck(player, StringArgumentType.getString(context, "name")) ?: return 0
    val shapeType = getShapeTypeCheck(player, StringArgumentType.getString(context, "shapeType").uppercase()) ?: return 0
    return regionCreationScheduler(player, regionName, shapeType)
}

private fun runDeleteRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java)
    return regionDeleteScheduler(player, regionIdentifier)
}

private fun runRenameRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java)
    val newName = context.getArgument("newName", String::class.java)
    return regionRenameScheduler(player, regionIdentifier, newName)
}

private fun runAddScopeById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    return try {
        val region = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        runAddScope(context, region)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runAddScopeByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    return try {
        val region = ImyvmWorldGeo.data.getRegionByName(regionName)
        runAddScope(context, region)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runAddScope(
    context: CommandContext<ServerCommandSource>,
    region: Region
): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid
    if (!selectionModeCheck(player)) return 0

    val shapeTypeName = getOptionalArgument(context, "shapeType")
        ?.uppercase() ?: return 0
    val shapeType = getShapeTypeCheck(player, shapeTypeName) ?: return 0
    val scopeNameArg = getOptionalArgument(context, "scopeName")
    val scopeName = getScopeNameCheck(player, region, scopeNameArg) ?: return 0

    return when (val creationResult = tryScopeCreation(playerUUID, scopeName, shapeType)) {
        is Result.Ok -> {
            handleScopeCreateSuccess(player, creationResult, region)
            1
        }
        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(errorMsg)
            0
        }
    }
}

private fun runDeleteScopeById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        runDeleteScope(player, targetRegion, scopeName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runDeleteScopeByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByName(regionName)
        runDeleteScope(player, targetRegion, scopeName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runDeleteScope(player: ServerPlayerEntity, region: Region, scopeName: String): Int {
    val existingScope = region.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }

    return if (existingScope != null) {
        region.geometryScope.remove(existingScope)
        player.sendMessage(Translator.tr("command.scope.delete.success", scopeName, region.name))
        1
    } else {
        player.sendMessage(Translator.tr("command.scope.scope_not_found", scopeName, region.name))
        0
    }
}

private fun runModifyScopeById(context: CommandContext<ServerCommandSource>): Int{
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        runModifyScope(player, targetRegion, scopeName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runModifyScopeByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByName(regionName)
        runModifyScope(player, targetRegion, scopeName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runModifyScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    val existingScope = targetRegion.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }

    return if (existingScope != null) {
        val playerUUID = player.uuid
        if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
            player.sendMessage(Translator.tr("command.select.not_in_mode"))
            return 0
        }

        val shapeType = existingScope.geoShape?.geoShapeType ?: Region.Companion.GeoShapeType.UNKNOWN
        if (shapeType == Region.Companion.GeoShapeType.UNKNOWN) {
            player.sendMessage(Translator.tr("command.scope.modify.unknown_shape_type"))
            return 0
        }

        val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: mutableListOf()
        if (shapeType == Region.Companion.GeoShapeType.POLYGON) {
            if (selectedPositions.size < 2) {
                player.sendMessage(Translator.tr("command.scope.modify.polygon_insufficient_points"))
                return 0
            } else if (selectedPositions.size == 2){
                modifyScopePolygonMove(player, targetRegion, existingScope, selectedPositions)
            } else {
                modifyScopePolygonInsertPoint(player, targetRegion, existingScope, selectedPositions)
            }
        } else if (shapeType == Region.Companion.GeoShapeType.CIRCLE) {
            if (selectedPositions.size == 1) {
                modifyScopeCircleRadius(player, targetRegion, existingScope, selectedPositions)
            } else{
                modifyScopeCircleCenter(player, targetRegion, existingScope, selectedPositions)
            }

        } else if (shapeType == Region.Companion.GeoShapeType.RECTANGLE) {
            modifyScopeRectangle(player, targetRegion, existingScope, selectedPositions)
        }
        1
    } else {
        player.sendMessage(Translator.tr("command.scope.scope_not_found", scopeName, targetRegion.name))
        0
    }
}

private fun runRenameScopeById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        runRenameScope(player, targetRegion, scopeName, newName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runRenameScopeByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)

    return try {
        val targetRegion = ImyvmWorldGeo.data.getRegionByName(regionName)
        runRenameScope(player, targetRegion, scopeName, newName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runRenameScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String,
    newName: String
): Int {
    val existingScope = targetRegion.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }

    if (existingScope == null) {
        player.sendMessage(Translator.tr("command.scope.scope_not_found", scopeName, targetRegion.name))
        return 0
    }

    if (existingScope.scopeName.equals(newName, ignoreCase = true)) {
        player.sendMessage(Translator.tr("command.scope.rename.repeated_same_name"))
        return 0
    }

    for (scope in targetRegion.geometryScope) {
        if (scope.scopeName.equals(newName, ignoreCase = true)) {
            player.sendMessage(Translator.tr("command.scope.rename.duplicate_scope_name"))
            return 0
        }
    }

    existingScope.scopeName = newName
    player.sendMessage(Translator.tr("command.scope.rename.success", scopeName, newName, targetRegion.name))
    return 1
}

private fun runAddSettingRegion(context: CommandContext<ServerCommandSource>): Int {
    TODO()
}

private fun runDeleteSettingRegion(context: CommandContext<ServerCommandSource>): Int {
    TODO()
}

private fun runAddSettingScope(context: CommandContext<ServerCommandSource>): Int {
    TODO()
}

private fun runDeleteSettingScope(context: CommandContext<ServerCommandSource>): Int {
    TODO()
}

private fun runQueryRegionById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    return try {
        val region = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        displayRegionInfo(context.source, region)
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runQueryRegionByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    return try {
        val region = ImyvmWorldGeo.data.getRegionByName(regionName)
        displayRegionInfo(context.source, region)
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun displayRegionInfo(source: ServerCommandSource, region: Region) {
    val player = source.player ?: return
    player.sendMessage(
        Translator.tr("command.query.result",
        region.name,
        region.numberID.toString(),
        region.calculateTotalArea())
    )

    val shapeInfos = region.getScopeInfos()
    shapeInfos.forEach { info ->
        player.sendMessage(info)
    }
}

private fun runListRegions(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regions = ImyvmWorldGeo.data.getRegionList()
    if (regions.isEmpty()) {
        player.sendMessage(Translator.tr("command.list.empty"))
        return 0
    }
    val regionList = regions.joinToString("\n") { "Region: ${it.name}, ID: ${it.numberID}" }
    player.sendMessage(Translator.tr("command.list.header", regionList))
    return 1
}

private fun runChangeDisplayMode(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    if (ImyvmWorldGeo.locationActionBarEnabledPlayers.contains(player.uuid)) {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.remove(player.uuid)
        player.sendMessage(Translator.tr("command.toggle.disabled"))
    } else {
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(player.uuid)
        player.sendMessage(Translator.tr("command.toggle.enabled"))
    }

    return 1
}