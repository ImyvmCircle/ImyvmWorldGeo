package com.imyvm.iwg.inter.register.command.helper

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

fun getPosArgument(
    context: CommandContext<ServerCommandSource>,
    name: String
): Int? {
    val rawArgument = getOptionalArgument(context, name) ?: return null

    val player = context.source.player ?: return null
    return try {
        rawArgument.toInt()
    } catch (e: NumberFormatException) {
        when (name.lowercase()) {
            "x" -> player.blockPos.x
            "y" -> player.blockPos.y
            "z" -> player.blockPos.z
            else -> null
        }
    }
}