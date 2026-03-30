package com.imyvm.iwg.application.selection.display

import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerPlayer

private val PILLAR_PARTICLE = DustParticleOptions(-1, 4.0f)
private const val PILLAR_STEP = 3

private val pendingPillarPositions = ThreadLocal.withInitial<MutableSet<Pair<Int, Int>>> { mutableSetOf() }

fun beginPillarTracking() {
    pendingPillarPositions.get().clear()
}

fun emitPillar(player: ServerPlayer, x: Int, z: Int) {
    val world = player.level()
    if (!world.chunkSource.hasChunk(x shr 4, z shr 4)) return
    pendingPillarPositions.get().add(x to z)
}

fun commitPillars(player: ServerPlayer) {
    val world = player.level()
    val bottom = world.minY
    val top = world.maxY
    pendingPillarPositions.get().forEach { (x, z) ->
        var y = bottom
        while (y <= top) {
            world.sendParticles(player, PILLAR_PARTICLE, true, false, x + 0.5, y.toDouble(), z + 0.5, 2, 0.0, 0.0, 0.0, 0.0)
            y += PILLAR_STEP
        }
    }
    pendingPillarPositions.get().clear()
}

fun clearSelectionDisplay(@Suppress("UNUSED_PARAMETER") player: ServerPlayer) = Unit