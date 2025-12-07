package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
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

fun getPlayerRegionScopeTriple(context: CommandContext<ServerCommandSource>): Triple<ServerPlayerEntity, Region, GeoScope>? {
    val player = context.source.player ?: return null

    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java) ?: return getPlayerRegionScopeTriple(player)
    val scopeName = context.getArgument("scopeName", String::class.java) ?: return getPlayerRegionScopeTriple(player)

    val region = try {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            val regionId = regionIdentifier.toInt()
            RegionDatabase.getRegionByNumberId(regionId)
        } else {
            RegionDatabase.getRegionByName(regionIdentifier)
        }
    } catch (e: RegionNotFoundException) {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("interaction.meta.not_found_id", regionIdentifier))
        } else {
            player.sendMessage(Translator.tr("interaction.meta.not_found_name", regionIdentifier))
        }
        null
    } ?: return getPlayerRegionScopeTriple(player)

    val scope = try {
        region.getScopeByName(scopeName)
    } catch (e: IllegalArgumentException) {
        return getPlayerRegionScopeTriple(player)
    }

    return Triple(player, region, scope)
}

private fun getPlayerRegionScopeTriple(playerEntity: ServerPlayerEntity): Triple<ServerPlayerEntity, Region, GeoScope>? {
    val x = playerEntity.blockX
    val z = playerEntity.blockZ
    val regionScopePair = RegionDatabase.getRegionAndScopeAt(playerEntity.world, x, z)

    return if (regionScopePair != null) {
        Triple(playerEntity, regionScopePair.first, regionScopePair.second)
    } else {
        playerEntity.sendMessage(Translator.tr("interaction.meta.scope.teleport_point.no_region"))
        null
    }
}