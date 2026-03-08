package com.imyvm.iwg.application.selection.display

import net.minecraft.server.network.ServerPlayerEntity

fun beginBeaconPillarTracking() = beginPillarTracking()

fun emitBeaconPillar(player: ServerPlayerEntity, x: Int, z: Int) = emitPillar(player, x, z)

fun commitBeaconPillars(player: ServerPlayerEntity) = commitPillars(player)

fun clearBeaconBeams(player: ServerPlayerEntity) = clearSelectionDisplay(player)
