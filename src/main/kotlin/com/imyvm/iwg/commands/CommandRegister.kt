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
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

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
                        literal("rectangle")
                            .executes { runCreateRegion(it, Region.Companion.GeoShapeType.RECTANGLE) }
                    )
                    .then(
                        literal("circle")
                            .executes { runCreateRegion(it, Region.Companion.GeoShapeType.CIRCLE) }
                    )
                    .then(
                        literal("polygon")
                            .executes { runCreateRegion(it, Region.Companion.GeoShapeType.POLYGON) }
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

private fun runCreateRegion(
    context: CommandContext<ServerCommandSource>,
    shapeType: Region.Companion.GeoShapeType
): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Translator.tr("command.select.not_in_mode"))
        return 0
    }

    val selectedPositions = ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]
    val creationResult = RegionFactory.createRegion(
        name = "NewRegion-${System.currentTimeMillis()}",
        numberID = ImyvmWorldGeo.data.getRegionList().size,
        selectedPositions = selectedPositions ?: mutableListOf(),
        shapeType = shapeType
    )

    when (creationResult) {
        is Result.Ok -> {
            val newRegion = creationResult.value
            ImyvmWorldGeo.data.addRegion(newRegion)
            ImyvmWorldGeo.logger.info("Created new region '${newRegion.name}' with positions: ${newRegion.geometryScope}")
            player.sendMessage(Translator.tr("command.create.success", newRegion.name))
            ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
            return 1
        }

        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(errorMsg)
            return 0
        }

        else -> {player.sendMessage(Translator.tr("error.unknown")); return 0}
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
        player.sendMessage(Translator.tr("command.delete.not_found_id", regionId))
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
        player.sendMessage(Translator.tr("command.delete.not_found", regionName))
        0
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
        player.sendMessage(Translator.tr("command.query.not_found_id", regionId.toString()))
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
        player.sendMessage(Translator.tr("command.query.not_found_name", regionName))
        0
    }
}

private fun displayRegionInfo(source: ServerCommandSource, region: Region) {
    val player = source.player ?: return
    player.sendMessage(Translator.tr("command.query.result", region.name, region.numberID.toString()))

    val shapeInfos = region.getShapeInfos()
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
