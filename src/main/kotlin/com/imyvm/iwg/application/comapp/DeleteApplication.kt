package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.RegionNotFoundException
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun regionDeleteScheduler(player: ServerPlayerEntity, regionIdentifier: String): Int {
    return try {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            val regionId = regionIdentifier.toInt()
            val regionToDelete = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
            ImyvmWorldGeo.data.removeRegion(regionToDelete)
            player.sendMessage(Translator.tr("command.delete.success.id", regionId))
        } else {
            val regionToDelete = ImyvmWorldGeo.data.getRegionByName(regionIdentifier)
            ImyvmWorldGeo.data.removeRegion(regionToDelete)
            player.sendMessage(Translator.tr("command.delete.success.name", regionToDelete.name))
        }
        1
    }
    catch (e: RegionNotFoundException) {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.not_found_id", regionIdentifier))
        } else {
            player.sendMessage(Translator.tr("command.not_found_name", regionIdentifier))
        }
        0
    }
}