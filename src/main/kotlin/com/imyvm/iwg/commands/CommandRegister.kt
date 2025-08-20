package com.imyvm.iwg.commands

import com.imyvm.iwg.ImyvmWorldGeo
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

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
                net.minecraft.text.Text.literal("Selection mode started. Use a golden hoe to select positions."),
            )
            1
        } else {
            player.sendMessage(
                net.minecraft.text.Text.literal("You are already in selection mode."),
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
                net.minecraft.text.Text.literal("Selection mode stopped."),
            )
            1
        } else {
            player.sendMessage(
                net.minecraft.text.Text.literal("You are not in selection mode."),
            )
            0
        }
    }
    return 0
}