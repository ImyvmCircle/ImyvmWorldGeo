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
    val region = try {
        resolveRegionIdentifier(regionIdentifier)
    } catch (e: RegionNotFoundException) {
        notifyRegionNotFound(player, regionIdentifier)
        return 0
    }
    onRegionFound(region)
    return 1
}

internal fun resolveRegionIdentifier(regionIdentifier: String): Region {
    if (!regionIdentifier.all(Char::isDigit)) return RegionDatabase.getRegionByName(regionIdentifier)
    val regionId = regionIdentifier.toIntOrNull()
        ?: throw RegionNotFoundException("Region ID '$regionIdentifier' is outside the supported range.")
    return RegionDatabase.getRegionByNumberId(regionId)
}

internal fun notifyRegionNotFound(player: ServerPlayer, regionIdentifier: String) {
    val key = if (regionIdentifier.all(Char::isDigit)) {
        "interaction.meta.not_found_id"
    } else {
        "interaction.meta.not_found_name"
    }
    player.sendSystemMessage(Translator.tr(key, regionIdentifier))
}
