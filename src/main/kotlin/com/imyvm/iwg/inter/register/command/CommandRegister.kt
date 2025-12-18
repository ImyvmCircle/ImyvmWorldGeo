package com.imyvm.iwg.inter.register.command

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.register.command.helper.*
import com.imyvm.iwg.util.text.Translator
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource

fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
    dispatcher.register(
        literal("imyvmWorldGeo")
            .then(
                literal("select")
                    .then(literal("start").executes { runStartSelect(it) })
                    .then(literal("stop").executes { runStopSelect(it) })
                    .then(literal("reset").executes { runResetSelect(it) })
            )
            .then(
                literal("create")
                    .requires { it.hasPermissionLevel(2) }
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
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runDeleteRegion(it) }
                    )
            )
            .then(
                literal("rename")
                    .requires { it.hasPermissionLevel(2) }
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
                    .requires { it.hasPermissionLevel(2) }
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
                literal("deleteScope")
                    .requires { it.hasPermissionLevel(2) }
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
                    .requires { it.hasPermissionLevel(2) }
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
                    .requires { it.hasPermissionLevel(2) }
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
                    .requires { it.hasPermissionLevel(2) }
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
                                        argument("key", StringArgumentType.word())
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
                                argument("regionIdentifier", StringArgumentType.word())
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
                    .requires { it.hasPermissionLevel(2) }
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
                                        argument("scopeName", StringArgumentType.word())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.word())
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
                                argument("regionIdentifier", StringArgumentType.word())
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
    val nameArg = getOptionalArgument(context, "name")
    val shapeTypeArg = StringArgumentType.getString(context, "shapeType").uppercase()
    return onRegionCreation(player, nameArg, shapeTypeArg, idMark = 0)
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

private fun runRenameScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRenameScope ->
        onScopeRename(player, regionToRenameScope, scopeName, newName)}
}

private fun runSetTeleportPoint(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    var x = getPosArgument(context, "x")
    var y = getPosArgument(context, "y")
    var z = getPosArgument(context, "z")
    if (x == null || y == null || z == null) {
        x = player.blockX
        y = player.blockY
        z = player.blockZ
    }

    val regionScopePair = RegionDatabase.getRegionAndScopeAt(player.world,x,z)
    if (regionScopePair == null) {
        player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.no_region"))
        return 0
    }

    return onAddingTeleportPoint(player, regionScopePair.first, regionScopePair.second, x, y, z)
}

private fun runResetTeleportPoint(context: CommandContext<ServerCommandSource>): Int {
    val ( _, _, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    return onResettingTeleportPoint(scope)
}

private fun runInquiryTeleportPoint(context: CommandContext<ServerCommandSource>): Int {
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    val teleportPoint = onGettingTeleportPoint(scope)
    return if (teleportPoint != null) {
        player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.result",
            teleportPoint.x, teleportPoint.y, teleportPoint.z,
            scope.scopeName,
            region)
        )
        1
    } else {
        player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.no_point",
            scope.scopeName,
            region)
        )
        0
    }
}

private fun runTeleportPlayer(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        try {
            val scope = regionToTeleport.getScopeByName(scopeName)
            onTeleportingPlayer(player, regionToTeleport, scope)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(Translator.tr(e.message))
        }
    }
}

private fun runTeleportPlayerToRegion(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        val (region, scope) = RegionDatabase.getRegionAndScope(regionToTeleport, regionToTeleport.geometryScope.firstOrNull()?.scopeName ?: "")
        if (scope != null) {
            onTeleportingPlayer(player, region, scope)
        } else {
            player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.no_scope", region.name))
            0
        }
    }
}

private fun runToggleTeleportPointAccessibility(context: CommandContext<ServerCommandSource>): Int{
    val (player, region, scope) = getPlayerRegionScopeTriple(context) ?: return 0
    player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.toggle", region.name, scope.scopeName))
    return onTogglingTeleportPointAccessibility(scope)
}

private fun runModifyScope(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToModifyScope -> onModifyScope(player, regionToModifyScope, scopeName)}
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

private fun runQuerySettingValue(context: CommandContext<ServerCommandSource>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { regionToQuery ->
        onCertificatePermissionValue(player, regionToQuery, scopeName, targetPlayer, keyString) }
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

private fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    return onHelp(player)
}