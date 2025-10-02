package com.imyvm.iwg.inter.register.helper

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

fun getPlayerRegionPair(context: CommandContext<ServerCommandSource>): Pair<ServerPlayerEntity, String>? {
    val player = context.source.player ?: return null
    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java)
    return player to regionIdentifier
}


fun getOptionalArgument(
    context: CommandContext<ServerCommandSource>,
    name: String
): String? = try {
    context.getArgument(name, String::class.java)
} catch (e: IllegalArgumentException) {
    null
}