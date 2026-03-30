package com.imyvm.iwg.inter.register.command

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.entrypoint.register.command.helper.*
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.register.command.helper.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.commands.Commands as MinecraftCommands
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
    dispatcher.register(
        literal("imyvmWorldGeo")
            .then(
                literal("select")
                    .then(
                        literal("start")
                            .executes { runStartSelect(it) }
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runStartSelectWithShape(it) })
                    )
                    .then(literal("stop").executes { runStopSelect(it) })
                    .then(
                        literal("reset")
                            .executes { runResetSelect(it) }
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runResetSelectWithShape(it) })
                    )
                    .then(
                        literal("shape")
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runSetSelectionShape(it) })
                    )
                    .then(
                        literal("modifyScope")
                            .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runStartSelectForModify(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("create")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .executes { runCreateRegion(it) }
                    .then(
                        argument("name", StringArgumentType.string())
                            .executes { runCreateRegion(it) }
                    )
            )
            .then(
                literal("delete")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runDeleteRegion(it) }
                    )
            )
            .then(
                literal("rename")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("newName", StringArgumentType.string())
                                    .executes { runRenameRegion(it) }
                            )
                    )
            )
            .then(
                literal("addScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runAddScope(it) }
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runAddScope(it) }
                            )
                    )
            )
            .then(
                literal("deleteScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runDeleteScope(it) }
                            )
                    )
            )
            .then(
                literal("teleportPoint")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("set")
                            .executes { runSetTeleportPoint(it) }
                            .then(
                                argument("x", StringArgumentType.word())
                                    .then(
                                        argument("y", StringArgumentType.word())
                                            .then(
                                                argument("z", StringArgumentType.word())
                                                    .executes { runSetTeleportPoint(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("reset")
                            .executes { runResetTeleportPoint(it) }
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runResetTeleportPoint(it) }
                                    )
                            )
                    )
                    .then(
                        literal("inquiry")
                            .executes { runInquiryTeleportPoint(it) }
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runInquiryTeleportPoint(it) }
                                    )
                            )
                    )
                    .then(
                        literal("teleport")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runTeleportPlayer(it) }
                                    )
                            )
                    )
                    .then(
                        literal("toggle")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runToggleTeleportPointAccessibility(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("teleport")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runTeleportPlayerToRegion(it) }
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runTeleportPlayer(it) }
                            )
                    )
            )
            .then(
                literal("modifyScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runModifyScope(it) }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { runRenameScope(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("transferScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("targetRegionIdentifier", StringArgumentType.string())
                                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                            .executes { runTransferScope(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("mergeRegion")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("targetRegionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .executes { runMergeRegion(it) }
                            )
                    )
            )
            .then(
                literal("setting")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("value", StringArgumentType.string())
                                                    .executes { runAddDeleteSetting(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runAddDeleteSetting(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("remove")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .executes { runAddDeleteSetting(it) }
                                            .then(
                                                argument("playerName", StringArgumentType.string())
                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                    .executes { runAddDeleteSetting(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("queryValue")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .executes { runQuerySettingValue(it) }
                                            .then(
                                                argument("playerName", StringArgumentType.string())
                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                    .executes { runQuerySettingValue(it) }
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("settingScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .then(
                                                        argument("value", StringArgumentType.string())
                                                            .executes { runAddDeleteSetting(it) }
                                                            .then(
                                                                argument("playerName", StringArgumentType.string())
                                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                                    .executes { runAddDeleteSetting(it) }
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
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .executes { runAddDeleteSetting(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runAddDeleteSetting(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("queryValue")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .executes { runQuerySettingValue(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runQuerySettingValue(it) }
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("dynmapToggle")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runToggleRegionDynmap(it) }
                    )
            )
            .then(
                literal("dynmapToggleScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runToggleScopeDynmap(it) }
                            )
                    )
            )
            .then(
                literal("query")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runQueryRegion(it) }
                    )
            )
            .then(literal("list").executes { runListRegions(it) })
            .then(literal("toggle").executes { runToggleActionBar(it) })
            .then(literal("help").executes { runHelp(it) })
    )
}

private fun runStartSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onStartSelection(player)
}

private fun runStopSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onStopSelection(player)
}

private fun runResetSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onResetSelection(player)
}

private fun runStartSelectWithShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onStartSelection(player, shapeType)
}

private fun runResetSelectWithShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onResetSelection(player, shapeType)
}

private fun runSetSelectionShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onSetSelectionShape(player, shapeType)
}

private fun parseShapeType(shapeTypeStr: String, player: ServerPlayer): GeoShapeType? {
    val shapeType = GeoShapeType.entries.find { it.name == shapeTypeStr } ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeStr)!!)
        return null
    }
    return shapeType
}

private fun runCreateRegion(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val nameArg = getOptionalArgument(context, "name")
    return onRegionCreation(player, nameArg, null, idMark = 0)
}

private fun runDeleteRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToDelete -> onRegionDelete(player, regionToDelete) }
}

private fun runRenameRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRename -> onRegionRename(player, regionToRename, newName) }
}

private fun runAddScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeNameArg = getOptionalArgument(context, "scopeName")
    return identifierHandler(regionIdentifier, player) { regionToAddScope -> onScopeCreation(player, regionToAddScope, scopeNameArg, null)}
}

private fun runDeleteScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToDeleteScope -> onScopeDelete(player, regionToDeleteScope, scopeName)}
}

private fun runRenameScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRenameScope ->
        onScopeRename(player, regionToRenameScope, scopeName, newName)}
}

private fun runTransferScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val targetRegionIdentifier = context.getArgument("targetRegionIdentifier", String::class.java)
    return identifierHandler(regionIdentifier, player) { sourceRegion ->
        identifierHandler(targetRegionIdentifier, player) { targetRegion ->
            onScopeTransfer(player, sourceRegion, scopeName, targetRegion)
        }
    }
}

private fun runMergeRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val targetRegionIdentifier = context.getArgument("targetRegionIdentifier", String::class.java)
    return identifierHandler(regionIdentifier, player) { sourceRegion ->
        identifierHandler(targetRegionIdentifier, player) { targetRegion ->
            onRegionMerge(player, sourceRegion, targetRegion)
        }
    }
}

private fun runSetTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    var x = getPosArgument(context, "x")
    var y = getPosArgument(context, "y")
    var z = getPosArgument(context, "z")
    if (x == null || y == null || z == null) {
        x = player.blockPosition().x
        y = player.blockPosition().y
        z = player.blockPosition().z
    }

    val regionScopePair = RegionDatabase.getRegionAndScopeAt(player.level(),x,z)
    if (regionScopePair == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.no_region")!!)
        return 0
    }

    return onAddingTeleportPoint(player, regionScopePair.first, regionScopePair.second, x, y, z)
}

private fun runResetTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    return onResettingTeleportPoint(player, region, scope)
}

private fun runInquiryTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    val teleportPoint = onGettingTeleportPoint(scope)
    return if (teleportPoint != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.result",
            teleportPoint.x, teleportPoint.y, teleportPoint.z,
            scope.scopeName,
            region)!!)
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.no_point",
            scope.scopeName,
            region)!!)
        0
    }
}

private fun runTeleportPlayer(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        try {
            val scope = regionToTeleport.getScopeByName(scopeName)
            onTeleportingPlayer(player, regionToTeleport, scope)
        } catch (e: IllegalArgumentException) {
            player.sendSystemMessage(Translator.tr(e.message)!!)
        }
    }
}

private fun runTeleportPlayerToRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        val (region, scope) = RegionDatabase.getRegionAndScope(regionToTeleport, regionToTeleport.geometryScope.firstOrNull()?.scopeName ?: "")
        if (scope != null) {
            onTeleportingPlayer(player, region, scope)
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.no_scope", region.name)!!)
            0
        }
    }
}

private fun runToggleTeleportPointAccessibility(context: CommandContext<CommandSourceStack>): Int{
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.toggle", region.name, scope.scopeName)!!)
    return onTogglingTeleportPointAccessibility(scope)
}

private fun runModifyScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToModifyScope -> onModifyScope(player, regionToModifyScope, scopeName)}
}

private fun runAddDeleteSetting(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val valueString = getOptionalArgument(context, "value")
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { regionToAddSetting ->
        onHandleSetting(player, regionToAddSetting, scopeName, keyString, valueString, targetPlayer) }
}

private fun runQuerySettingValue(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { regionToQuery ->
        onQuerySettingValue(player, regionToQuery, scopeName, keyString, targetPlayer) }
}

private fun runQueryRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToQuery -> onQueryRegion(player, regionToQuery, false) }
}

private fun runListRegions(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onListRegions(player)
}

private fun runToggleActionBar(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onToggleActionBar(player)
}

private fun runHelp(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onHelp(player)
}

private fun runStartSelectForModify(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { region ->
        try {
            val scope = region.getScopeByName(scopeName)
            onStartSelectionForModify(player, scope)
        } catch (e: IllegalArgumentException) {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.add.not_found_generic")!!)
            0
        }
    }
}

private fun runToggleRegionDynmap(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { region -> onTogglingRegionDynmap(player, region) }
}

private fun runToggleScopeDynmap(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    return onTogglingScopeDynmap(player, region, scope)
}