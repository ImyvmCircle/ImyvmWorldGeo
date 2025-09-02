package com.imyvm.iwg.inter.api

import net.minecraft.server.network.ServerPlayerEntity

object ImyvmWorldGeoApi {
    fun startSelection(player: ServerPlayerEntity): Int{
        return startSelection(player)
    }

    fun stopSelection(player: ServerPlayerEntity): Int{
        return stopSelection(player)
    }

    fun resetSelection(player: ServerPlayerEntity): Int{
        return resetSelection(player)
    }
}