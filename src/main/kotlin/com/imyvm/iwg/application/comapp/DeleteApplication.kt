package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onRegionDelete(player: ServerPlayerEntity, region: Region){
    val regionName = region.name
    val regionId = region.numberID
    ImyvmWorldGeo.data.removeRegion(region)
    player.sendMessage(Translator.tr("command.delete.success", regionName, regionId))
}