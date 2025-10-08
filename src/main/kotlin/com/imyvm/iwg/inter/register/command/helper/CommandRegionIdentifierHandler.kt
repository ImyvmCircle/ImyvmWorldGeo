package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun identifierHandler(
    regionIdentifier: String,
    player: ServerPlayerEntity,
    onRegionFound: (region: Region) -> Unit
): Int {
    return try {
        val region: Region = if (regionIdentifier.matches("\\d+".toRegex())) {
            val regionId = regionIdentifier.toInt()
            RegionDatabase.getRegionByNumberId(regionId)
        } else {
            RegionDatabase.getRegionByName(regionIdentifier)
        }
        onRegionFound(region)
        1
    } catch (e: RegionNotFoundException) {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("interaction.meta.not_found_id", regionIdentifier))
        } else {
            player.sendMessage(Translator.tr("interaction.meta.not_found_name", regionIdentifier))
        }
        0
    }
}