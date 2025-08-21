package com.imyvm.iwg.commands

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.region.CreationError
import com.imyvm.iwg.region.Result
import com.imyvm.iwg.region.Region
import com.imyvm.iwg.region.RegionFactory
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
                        argument("name", StringArgumentType.string())
                            .executes { runDeleteRegionsByName(it) }
                    )
                    .then(
                        argument("id", IntegerArgumentType.integer())
                            .executes { runDeleteRegionsById(it) }
                    )
            )
            .then(
                literal("list")
                    .executes { runlistRegions(it) }
            )
    )
}

private fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    TODO("Implement help command logic here")
}

private fun runStartSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player
    if (player != null) {
        val playerUUID = player.uuid
        return if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
            ImyvmWorldGeo.commandlySelectingPlayers[playerUUID] = mutableListOf()

            ImyvmWorldGeo.logger.info("Player $playerUUID has started selection mode, players in selection mod: ${ImyvmWorldGeo.commandlySelectingPlayers.keys}")
            player.sendMessage(
                Text.literal("Selection mode started. Use a golden hoe to select positions."),
            )
            1
        } else {
            player.sendMessage(
                Text.literal("You are already in selection mode."),
            )
            0
        }
    }
    return 0
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player
    if (player != null) {
        val playerUUID = player.uuid
        return if (ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
            ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)

            ImyvmWorldGeo.logger.info("Player $playerUUID has stopped selection mode, players in selection mod: ${ImyvmWorldGeo.commandlySelectingPlayers.keys}")
            player.sendMessage(
                Text.literal("Selection mode stopped."),
            )
            1
        } else {
            player.sendMessage(
                Text.literal("You are not in selection mode."),
            )
            0
        }
    }
    return 0
}

private fun runCreateRegion(context: CommandContext<ServerCommandSource>, shapeType: Region.Companion.GeoShapeType): Int {
    val player = context.source.player ?: return 0

    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        player.sendMessage(Text.literal("You are not in selection mode."))
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
            player.sendMessage(Text.literal("Region '${newRegion.name}' created successfully!"))
            ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
            return 1
        }
        is Result.Err -> {
            val errorMsg = when (creationResult.error) {
                CreationError.DuplicatedPoints -> "Cannot create a region with duplicated points."
                CreationError.InsufficientPoints -> "You must select enough points for a ${shapeType.name.lowercase()} region."
                CreationError.CoincidentPoints -> "For a rectangle, points must not share an X or Z coordinate."
                CreationError.UnderSizeLimit -> "The region is too small. It must be at least 1x1 area for a rectangle or have a radius of at least 1 for a circle."
                CreationError.NotConvex -> "The polygon must be convex."
            }
            player.sendMessage(Text.literal(errorMsg))
            return 0
        }
    }
}

private fun runDeleteRegionsByName(context: CommandContext<ServerCommandSource>): Int {
    val regionName = context.getArgument("name", String::class.java)
    val notFoundMessage = Text.literal("Region '$regionName' not found.")
    return runDeleteRegions(context, { it.name.equals(regionName, ignoreCase = true) }, notFoundMessage)
}

private fun runDeleteRegionsById(context: CommandContext<ServerCommandSource>): Int {
    val regionId = context.getArgument("id", Int::class.java)
    val notFoundMessage = Text.literal("Region with ID '$regionId' not found.")
    return runDeleteRegions(context, { it.numberID == regionId }, notFoundMessage)
}

private fun runDeleteRegions(context: CommandContext<ServerCommandSource>, findPredicate: (Region) -> Boolean, notFoundMessage: Text): Int {
    val player = context.source.player ?: return 0

    val regions = ImyvmWorldGeo.data.getRegionList()
    if (regions.isEmpty()) {
        player.sendMessage(Text.literal("No regions found."))
        return 0
    }

    val regionToDelete = regions.find { findPredicate(it) }

    return if (regionToDelete != null) {
        ImyvmWorldGeo.data.removeRegion(regionToDelete)
        player.sendMessage(Text.literal("Region '${regionToDelete.name}' deleted successfully!"))
        1
    } else {
        player.sendMessage(notFoundMessage)
        0
    }
}

private fun runlistRegions(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0

    val regions = ImyvmWorldGeo.data.getRegionList()
    if (regions.isEmpty()) {
        player.sendMessage(Text.literal("No regions found."))
        return 0
    }

    val regionList = regions.joinToString("\n") { "Region: ${it.name}, ID: ${it.numberID}, Scopes: ${it.geometryScope.size}" }
    player.sendMessage(Text.literal("Regions:\n$regionList"))
    return 1
}