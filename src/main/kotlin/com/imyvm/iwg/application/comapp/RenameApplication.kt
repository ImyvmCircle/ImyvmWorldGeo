package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity


fun renameRegion(player: ServerPlayerEntity, region: Region, newName: String): Int {
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