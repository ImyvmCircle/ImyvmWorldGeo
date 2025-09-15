package com.imyvm.iwg.util.command

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

fun getOptionalArgument(
    context: CommandContext<ServerCommandSource>,
    name: String
): String? = try {
    context.getArgument(name, String::class.java)
} catch (e: IllegalArgumentException) {
    null
}