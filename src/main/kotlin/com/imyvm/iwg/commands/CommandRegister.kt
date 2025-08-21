package com.imyvm.iwg.commands

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.commands.ErrorMessages.ERR_CIRCLE_TOO_SMALL
import com.imyvm.iwg.commands.ErrorMessages.ERR_COINCIDENT_POINTS
import com.imyvm.iwg.commands.ErrorMessages.ERR_DUPLICATED_POINTS
import com.imyvm.iwg.commands.ErrorMessages.ERR_GENERIC_TOO_SMALL
import com.imyvm.iwg.commands.ErrorMessages.ERR_INSUFFICIENT_POINTS
import com.imyvm.iwg.commands.ErrorMessages.ERR_NOT_CONVEX
import com.imyvm.iwg.commands.ErrorMessages.ERR_POLYGON_TOO_SMALL
import com.imyvm.iwg.commands.ErrorMessages.ERR_RECTANGLE_TOO_SMALL
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
                literal("list")
                    .executes { runlistRegions(it) }
            )
    )
}

private fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player
    player?.sendMessage(
        Text.literal(
            """
            |Imyvm World Geo Commands:
            |/imyvm-world-geo help - Show this help message.
            |/imyvm-world-geo select start - Start selecting positions with a golden hoe.
            |/imyvm-world-geo select stop - Stop selection mode.
            |/imyvm-world-geo select reset - Clear all selected points but keep selection mode active.
            |/imyvm-world-geo create rectangle - Create a rectangular region from selected positions.
            |/imyvm-world-geo create circle - Create a circular region from selected positions.
            |/imyvm-world-geo create polygon - Create a polygonal region from selected positions.
            |/imyvm-world-geo delete id <id> - Delete a region by its ID.
            |/imyvm-world-geo delete name <name> - Delete a region by its name.
            |/imyvm-world-geo list - List all regions.
            """.trimMargin()
        )
    )
    return 1
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

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val playerUUID = player.uuid

    return if (ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.commandlySelectingPlayers[playerUUID] = mutableListOf()

        ImyvmWorldGeo.logger.info("Player $playerUUID has reset their selection points.")
        player.sendMessage(Text.literal("Your selection points have been reset. You are still in selection mode."))
        1
    } else {
        player.sendMessage(Text.literal("You are not in selection mode. Use /imyvm-world-geo select start first."))
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
            ImyvmWorldGeo.logger.info(
                "Created new region '${newRegion.name}' with positions: ${newRegion.geometryScope}"
            )
            player.sendMessage(Text.literal("Region '${newRegion.name}' created successfully!"))
            ImyvmWorldGeo.commandlySelectingPlayers.remove(playerUUID)
            return 1
        }

        is Result.Err -> {
            val errorMsg = errorMessage(creationResult.error, shapeType)
            player.sendMessage(Text.literal(errorMsg))
            return 0
        }
    }
}

private fun runDeleteRegionsByName(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0

    val regionName = context.getArgument("name", String::class.java)

    return try {
        val regionToDelete = ImyvmWorldGeo.data.getRegionByName(regionName)
        ImyvmWorldGeo.data.removeRegion(regionToDelete)
        player.sendMessage(Text.literal("Region '${regionToDelete.name}' deleted successfully!"))
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Text.literal(e.message))
        0
    }
}

private fun runDeleteRegionsById(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0

    val regionId = context.getArgument("id", Int::class.java)

    return try {
        val regionToDelete = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
        ImyvmWorldGeo.data.removeRegion(regionToDelete)
        player.sendMessage(Text.literal("Region with ID '$regionId' deleted successfully!"))
        1
    } catch (e: RegionNotFoundException) {
        player.sendMessage(Text.literal(e.message))
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

    val regionList = regions.joinToString("\n") { "Region: ${it.name}, ID: ${it.numberID}" }
    player.sendMessage(Text.literal("Regions:\n$regionList"))
    return 1
}

private fun errorMessage(
    error: CreationError,
    shapeType: Region.Companion.GeoShapeType
): String = when (error) {
    CreationError.DuplicatedPoints -> ERR_DUPLICATED_POINTS
    CreationError.InsufficientPoints -> ERR_INSUFFICIENT_POINTS.replace("{shape}", shapeType.name.lowercase())
    CreationError.CoincidentPoints -> ERR_COINCIDENT_POINTS
    CreationError.UnderSizeLimit -> when (shapeType) {
        Region.Companion.GeoShapeType.RECTANGLE -> ERR_RECTANGLE_TOO_SMALL
        Region.Companion.GeoShapeType.CIRCLE -> ERR_CIRCLE_TOO_SMALL
        Region.Companion.GeoShapeType.POLYGON -> ERR_POLYGON_TOO_SMALL
        else -> ERR_GENERIC_TOO_SMALL
    }
    CreationError.NotConvex -> ERR_NOT_CONVEX
}

private object ErrorMessages {
    const val ERR_DUPLICATED_POINTS =
        "Cannot create a region with duplicated points."

    const val ERR_INSUFFICIENT_POINTS =
        "You must select enough points for a {shape} region."

    const val ERR_COINCIDENT_POINTS =
        "For a rectangle, the first two points must not share the same X or Z coordinate."

    const val ERR_RECTANGLE_TOO_SMALL =
        "The rectangle is too small. It must meet the minimum side length and area requirements."

    const val ERR_CIRCLE_TOO_SMALL =
        "The circle is too small. The radius must be at least the minimum size."

    const val ERR_POLYGON_TOO_SMALL =
        "The polygon is too small. The area must be at least the minimum size."

    const val ERR_GENERIC_TOO_SMALL =
        "The region is too small."

    const val ERR_NOT_CONVEX =
        "The polygon must be convex."
}