package com.imyvm.iwg.inter.register

import com.imyvm.iwg.application.comapp.*
import com.imyvm.iwg.util.command.*
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource

fun register(dispatcher: CommandDispatcher<ServerCommandSource>, registryAccess: CommandRegistryAccess) {
    dispatcher.register(
        literal("imyvm-world-geo")
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
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runDeleteRegion(it) }
                    )
            )
            .then(
                literal("rename")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
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
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .executes { runAddScope(it) }
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .executes { runAddScope(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("deletescope")
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
                literal("modifyscope")
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
                literal("setting")
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("settingType", StringArgumentType.string())
                                            .suggests(SETTING_TYPE_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .then(
                                                        argument("value", StringArgumentType.string())
                                                            .executes{ runAddDeleteSetting(it) }
                                                            .then(
                                                                argument("playerName", StringArgumentType.string())
                                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                                    .executes{ runAddDeleteSetting(it) }
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
                                        argument("settingType", StringArgumentType.word())
                                            .suggests(SETTING_TYPE_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.word())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .executes{ runAddDeleteSetting(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes{ runAddDeleteSetting(it) }
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
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("settingType", StringArgumentType.string())
                                                    .suggests(SETTING_TYPE_SUGGESTION_PROVIDER)
                                                    .then(
                                                        argument("key", StringArgumentType.string())
                                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                            .then(
                                                                argument("value", StringArgumentType.string())
                                                                    .executes{ runAddDeleteSetting(it) }
                                                                    .then(
                                                                        argument("playerName", StringArgumentType.string())
                                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                                            .executes{ runAddDeleteSetting(it) }

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
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.word())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("settingType", StringArgumentType.word())
                                                    .suggests(SETTING_TYPE_SUGGESTION_PROVIDER)
                                                    .then(
                                                        argument("key", StringArgumentType.word())
                                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                            .executes{ runAddDeleteSetting(it) }
                                                            .then(
                                                                argument("playerName", StringArgumentType.string())
                                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                                    .executes{ runAddDeleteSetting(it) }
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
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes{ runQueryRegion(it) }
                    )
            )
            .then(
                literal("list")
                    .executes { runListRegions(it) }
            )
            .then(
                literal("toggle")
                    .executes{ runToggleActionBar(it) }
            )
            .then(
                literal("help")
                    .executes { runHelp(it) }
            )
    )
}

private fun runStartSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onStartSelection(player)
}

private fun runStopSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onStopSelection(player)
}

private fun runResetSelect(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onResetSelection(player)
}

private fun runCreateRegion(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    val nameArg = StringArgumentType.getString(context, "name")
    val shapeTypeArg = StringArgumentType.getString(context, "shapeType").uppercase()
    return onRegionCreation(player, nameArg, shapeTypeArg, isApi = false)
}

private fun runDeleteRegion(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToDelete -> onRegionDelete(player, regionToDelete) }
}

private fun runRenameRegion(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRename -> onRegionRename(player, regionToRename, newName) }
}

private fun runAddScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeNameArg = getOptionalArgument(context, "scopeName")
    val shapeTypeName = getOptionalArgument(context, "shapeType")?.uppercase() ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToAddScope -> onScopeCreation(player, regionToAddScope, scopeNameArg, shapeTypeName)}
}

private fun runDeleteScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToDeleteScope -> onScopeDelete(player, regionToDeleteScope, scopeName)}
}

private fun runModifyScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToModifyScope -> onModifyScope(player, regionToModifyScope, scopeName)}
}

private fun runRenameScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRenameScope ->
        onScopeRename(player, regionToRenameScope, scopeName, newName)}
}

private fun runAddDeleteSetting(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val valueString = getOptionalArgument(context, "value")
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { regionToAddSetting ->
        onHandleSetting(player, regionToAddSetting, scopeName, keyString, valueString, targetPlayer) }
}

private fun runQueryRegion(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToQuery -> onQueryRegion(player, regionToQuery, false) }
}

private fun runListRegions(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onListRegions(player)
}

private fun runToggleActionBar(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onToggleActionBar(player)
}

fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onHelp(player)
}