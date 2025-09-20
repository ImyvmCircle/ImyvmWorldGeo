package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.RegionNotFoundException
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun regionRenameScheduler(player: ServerPlayerEntity, regionIdentifier: String, newName: String): Int {
    if (newName.matches("\\d+".toRegex())) {
        player.sendMessage(Translator.tr("command.rename.name_is_digits_only"))
        return 0
    }

    return try {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            val regionId = regionIdentifier.toInt()
            val region = ImyvmWorldGeo.data.getRegionByNumberId(regionId)
            return renameRegionCore(player, region, newName)
        } else {
            val region = ImyvmWorldGeo.data.getRegionByName(regionIdentifier)
            return renameRegionCore(player, region, newName)
        }
    } catch (e: RegionNotFoundException) {
        if (regionIdentifier.matches("\\d+".toRegex())) {
            player.sendMessage(Translator.tr("command.not_found_id", regionIdentifier))
        } else {
            player.sendMessage(Translator.tr("command.not_found_name", regionIdentifier))
        }
        0
    }
}

fun renameRegionCore(player: ServerPlayerEntity, region: Region, newName: String): Int {
    val oldName = region.name

    if (oldName == newName) {
        player.sendMessage(Translator.tr("command.rename.repeated_same_name"))
        return 0
    }

    return try {
        ImyvmWorldGeo.data.renameRegion(region, newName)
        player.sendMessage(Translator.tr("command.rename.success", oldName, newName))
        1
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr("command.rename.duplicate_name", newName))
        0
    }
}