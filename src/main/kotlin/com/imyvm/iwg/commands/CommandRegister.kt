package com.imyvm.iwg.commands

import CreationError
import Result
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.ui.Translator
import com.imyvm.iwg.region.*
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.CompletableFuture

private val SHAPE_TYPE_SUGGESTION_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    Region.Companion.GeoShapeType.entries
        .filter { it != Region.Companion.GeoShapeType.UNKNOWN }
        .forEach { builder.suggest(it.name.lowercase(Locale.getDefault())) }
    CompletableFuture.completedFuture(builder.build())
}

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
                        argument("id", IntegerArgumentType.integer())
                            .executes { runDeleteRegionsById(it) }
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .executes { runDeleteRegionsByName(it) }
                    )
            )
            .then(
                literal("rename")
                    .then(
                        argument("id", IntegerArgumentType.integer())
                            .then(argument("newName", StringArgumentType.string())
                                .executes { runRenameRegionById(it) }
                            )
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .then(argument("newName", StringArgumentType.string())
                                .executes { runRenameRegionByName(it) }
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
                                    .executes { /* TODO: Implement modify shape by region ID and scope name */ 0 }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { /* TODO: rename scope */ 0 }
                                    )
                            )
                    )
                    .then(
                        argument("name", StringArgumentType.string())
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { /* TODO: Implement modify shape by region name and scope name */ 0 }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { /*TODO: rename scope */ 0 }
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
    )
}

private fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    player.sendMessage(Translator.tr("command.help"))
    return 1
}

private fun runStartSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.commandlySelectingPlayers[playerUUID] = mutableListOf()
        ImyvmWorldGeo.logger.info("Player $playerUUID has started selection mode, players in selection mod: ${ImyvmWorldGeo.commandlySelectingPlayers.keys}")
        player.sendMessage(Translator.tr("command.select.start"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.already"))
        0
    }
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
        ImyvmWorldGeo.logger.info("Player $playerUUID has stopped selection mode, players in selection mod: ${ImyvmWorldGeo.commandlySelectingPlayers.keys}")
        player.sendMessage(Translator.tr("command.select.stop"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.commandlySelectingPlayers[playerUUID] = mutableListOf()
        ImyvmWorldGeo.logger.info("Player $playerUUID has reset their selection points.")
        player.sendMessage(Translator.tr("command.select.reset"))
        1
    } else {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        0
    }
}

private fun runCreateRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid

    if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        return 0
    }

    val regionName: String = try {
        val nameArgument = StringArgumentType.getString(context, "name")
        if (nameArgument.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.create.name_is_digits_only"))
            return 0
        }
        nameArgument
    } catch (e: IllegalArgumentException) {
        "NewRegion-${System.currentTimeMillis()}"
    }

    val shapeTypeName = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = Region.Companion.GeoShapeType.entries
        .find { it.name == shapeTypeName }
        ?: Region.Companion.GeoShapeType.UNKNOWN

    if (shapeType == Region.Companion.GeoShapeType.UNKNOWN) {
        player.sendMessage(Translator.tr("command.create.invalid_shape", shapeTypeName))
        return 0
    }

    val selectedPositions = ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]
    val biggestId = ImyvmWorldGeo.data.getRegionList().maxOfOrNull { it.numberID } ?: -1
    val newID = biggestId + 1
    val creationResult = RegionFactory.createRegion(
        name = regionName,
        numberID = newID,
        selectedPositions = selectedPositions ?: mutableListOf(),
        shapeType = shapeType
    )

    return when (creationResult) {
        is Result.Ok -> {
            val newRegion = creationResult.value
            ImyvmWorldGeo.data.addRegion(newRegion)
            ImyvmWorldGeo.logger.info("Created new region '${newRegion.name}' with positions: ${newRegion.geometryScope}")
            player.sendMessage(Translator.tr("command.create.success", newRegion.name))
            ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
            1
        }
        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(errorMsg)
            0
        }
        else -> {
            player.sendMessage(Translator.tr("error.unknown"))
            0
        }
    }
}

private fun runDeleteRegionsById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    return try {
        val regionToDelete = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        ImyvmWorldGeo.data.removeRegion(regionToDelete)
        player.sendMessage(Translator.tr("command.delete.success.id", regionId))
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId))
        0
    }
}

private fun runDeleteRegionsByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    return try {
        val regionToDelete = ImyvmWorldGeo.data.getRegionByName(regionName)
        ImyvmWorldGeo.data.removeRegion(regionToDelete)
        player.sendMessage(Translator.tr("command.delete.success.name", regionToDelete.name))
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runRenameRegionById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionId = context.getArgument("id", Int::class.java)
    val newName = context.getArgument("newName", String::class.java)

    if (newName.matches("\\d+".toRegex())) {
        player.sendMessage(Translator.tr("command.rename.name_is_digits_only"))
        return 0
    }

    return try {
        val region = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        return runRenameRegionAndSendFeedback(player, region, newName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_id", regionId.toString()))
        0
    }
}

private fun runRenameRegionByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val regionName = context.getArgument("name", String::class.java)
    val newName = context.getArgument("newName", String::class.java)

    return try {
        val region = ImyvmWorldGeo.data.getRegionByName(regionName)
        return runRenameRegionAndSendFeedback(player, region, newName)
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Translator.tr("command.not_found_name", regionName))
        0
    }
}

private fun runRenameRegionAndSendFeedback(player: ServerPlayerEntity, region: Region, newName: String): Int {
    val oldName = region.name

    if (oldName == newName) {
        player.sendMessage(Translator.tr("command.rename.repeated_same_name"))
        return 0
    }

    return try {
        ImyvmWorldGeo.data.renameRegion(region, newName)
        player.sendMessage(Translator.tr("command.rename.success", oldName, newName))
        1
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("command.rename.duplicate_name", newName))
        0
    }
}

private fun runAddScopeById(context: CommandContext<ServerCommandSource>): Int {
    val regionId = context.getArgument("id", Int::class.java)
    return runAddScope(
        context = context,
        region = { ImyvmWorldGeo.data.getRegionByNumberId(regionId) },
        notFoundMessage = { Translator.tr("command.not_found_id", regionId.toString()) }
    )
}

private fun runAddScopeByName(context: CommandContext<ServerCommandSource>): Int {
    val regionName = context.getArgument("name", String::class.java)
    return runAddScope(
        context = context,
        region = { ImyvmWorldGeo.data.getRegionByName(regionName) },
        notFoundMessage = { Translator.tr("command.not_found_name", regionName) }
    )
}

private fun runAddScope(
    context: CommandContext<ServerCommandSource>,
    region: () -> Region?,
    notFoundMessage: () -> Text
): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid

    if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        return 0
    }

    val shapeTypeStr = context.getArgument("shapeType", String::class.java).uppercase()
    val shapeType: Region.Companion.GeoShapeType = try {
        Region.Companion.GeoShapeType.valueOf(shapeTypeStr)
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("command.addscope.invalid_shape_type"))
        return 0
    }

    val scopeName: String = try {
        context.getArgument("scopeName", String::class.java)
    } catch (e: IllegalArgumentException) {
        val targetRegion = region() ?: return 0
        "NewScope-${targetRegion.name}-${System.currentTimeMillis()}"
    }

    return try {
        val targetRegion = region() ?: throw RegionNotFoundException(Translator.tr("command.addscope.not_found_generic").string)

        for (existingScope in targetRegion.geometryScope) {
            if (existingScope.scopeName.equals(scopeName, ignoreCase = true)) {
                player.sendMessage(Translator.tr("command.addscope.duplicate_scope_name"))
                return 0
            }
        }

        val selectedPositions = ImyvmWorldGeo.commandlySelectingPlayers[playerUUID] ?: mutableListOf()

        val creationResult = RegionFactory.createScope(
            scopeName = scopeName,
            selectedPositions = selectedPositions,
            shapeType = shapeType
        )

        when (creationResult) {
            is Result.Ok -> {
                targetRegion.geometryScope.add(creationResult.value)
                player.sendMessage(Translator.tr("command.addscope.success", scopeName, targetRegion.name))
                ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
                1
            }
            is Result.Err -> {
                val errorMsg = errorMessage(creationResult.error, shapeType)
                player.sendMessage(errorMsg)
                0
            }
            else -> {
                player.sendMessage(Translator.tr("error.unknown"))
                0
            }
        }
    } catch (e: RegionNotFoundException) {
        player.sendMessage(notFoundMessage())
        0
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

    if (existingScope != null) {
        region.geometryScope.remove(existingScope)
        player.sendMessage(Translator.tr("command.deletescope.success", scopeName, region.name))
        return 1
    } else {
        player.sendMessage(Translator.tr("command.deletescope.scope_not_found", scopeName, region.name))
        return 0
    }
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
    player.sendMessage(Translator.tr("command.query.result",
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

private fun errorMessage(
    error: CreationError,
    shapeType: Region.Companion.GeoShapeType
): Text = when (error) {
    CreationError.DuplicatedPoints -> Translator.tr("error.duplicated_points")
    CreationError.InsufficientPoints -> Translator.tr("error.insufficient_points", shapeType.name.lowercase())
    CreationError.CoincidentPoints -> Translator.tr("error.coincident_points")
    CreationError.UnderSizeLimit -> when (shapeType) {
        Region.Companion.GeoShapeType.RECTANGLE -> Translator.tr("error.rectangle_too_small")
        Region.Companion.GeoShapeType.CIRCLE -> Translator.tr("error.circle_too_small")
        Region.Companion.GeoShapeType.POLYGON -> Translator.tr("error.polygon_too_small")
        else -> Translator.tr("error.generic_too_small")
    }
    CreationError.NotConvex -> Translator.tr("error.not_convex")
    CreationError.IntersectionBetweenScopes -> Translator.tr("error.intersection_between_scopes")
    else -> { Translator.tr("error.unknown") }
}