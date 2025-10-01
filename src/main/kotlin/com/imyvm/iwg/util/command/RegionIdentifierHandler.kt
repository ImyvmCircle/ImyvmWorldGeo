package com.imyvm.iwg.util.command

import com.imyvm.iwg.RegionDatabase
import com.imyvm.iwg.RegionNotFoundException
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
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
            player.sendMessage(Translator.tr("command.not_found_id", regionIdentifier))
        } else {
            player.sendMessage(Translator.tr("command.not_found_name", regionIdentifier))
        }
        0
    }
}