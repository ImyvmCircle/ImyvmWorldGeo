package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import net.minecraft.server.network.ServerPlayerEntity

fun onAddTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String,
    x: Int,
    y: Int,
    z: Int
): Int {
    TODO()
}

fun onRemoveTeleportPoint(
    playerExecutor: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    TODO()
}