package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

fun getPlayerAndRegionInfo(context: CommandContext<ServerCommandSource>): Triple<ServerPlayerEntity, Region, String>? {
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