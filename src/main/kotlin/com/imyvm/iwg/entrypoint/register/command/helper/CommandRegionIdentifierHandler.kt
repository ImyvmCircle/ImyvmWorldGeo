package com.imyvm.iwg.entrypoint.register.command.helper

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun identifierHandler(
    regionIdentifier: String,
    player: ServerPlayer,
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
            player.sendSystemMessage(Translator.tr("interaction.meta.not_found_id", regionIdentifier)!!)
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.not_found_name", regionIdentifier)!!)
        }
        0
    }
}