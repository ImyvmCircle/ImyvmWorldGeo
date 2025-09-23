package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.domain.Region
import net.minecraft.server.network.ServerPlayerEntity

fun onHandleSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String,
    isPersonal: Boolean,
    targetPlayerStr: String
){
    if (scopeName.isNullOrBlank()) {
        onHandleRegion(
            player,
            region,
            keyString,
            valueString,
            isPersonal,
            targetPlayerStr
        )
    } else {
        onHandleScope(
            player,
            region,
            scopeName,
            keyString,
            valueString,
            isPersonal,
            targetPlayerStr
        )
    }
}

private fun onHandleRegion(
    player: ServerPlayerEntity,
    region: Region,
    keyString: String,
    valueString: String,
    isPersonal: Boolean,
    targetPlayerStr: String
){

}

private fun onHandleScope(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?
    keyString: String,
    valueString: String,
    isPersonal: Boolean,
    targetPlayerStr: String
) {

}

