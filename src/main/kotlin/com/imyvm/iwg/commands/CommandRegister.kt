package com.imyvm.iwg.commands

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
    )

}

private fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    TODO("Implement help command logic here")
}

