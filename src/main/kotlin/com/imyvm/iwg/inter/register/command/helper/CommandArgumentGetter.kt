package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

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
        if (rawArgument != "~") {
            return null
        }

        when (name.lowercase()) {
            "x" -> player.blockPos.x
            "y" -> player.blockPos.y
            "z" -> player.blockPos.z
            else -> null
        }
    }
}

fun getPlayerRegionPair(context: CommandContext<ServerCommandSource>): Pair<ServerPlayerEntity, String>? {
    val player = context.source.player ?: return null
    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java)
    return player to regionIdentifier
}

fun getPlayerRegionScopeTriple(context: CommandContext<ServerCommandSource>): Triple<ServerPlayerEntity, Region, String>? {
    val player = context.source.player ?: return null
    val x = player.blockX
    val z = player.blockZ
    val regionScopePair = RegionDatabase.getRegionAndScopeAt(player.world, x, z)

    if (regionScopePair == null) {
        player.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.no_region"))
        return null
    }

    val region = regionScopePair.first
    val scopeName = regionScopePair.second.scopeName

    return Triple(player, region, scopeName)
}