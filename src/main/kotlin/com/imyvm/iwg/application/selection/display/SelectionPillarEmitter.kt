package com.imyvm.iwg.application.selection.display

import net.minecraft.particle.DustParticleEffect
import net.minecraft.server.network.ServerPlayerEntity
import org.joml.Vector3f

private val PILLAR_PARTICLE = DustParticleEffect(Vector3f(1.0f, 1.0f, 1.0f), 4.0f)
private const val PILLAR_STEP = 3

private val pendingPillarPositions = ThreadLocal.withInitial<MutableSet<Pair<Int, Int>>> { mutableSetOf() }

fun beginPillarTracking() {
    pendingPillarPositions.get().clear()
}

fun emitPillar(player: ServerPlayerEntity, x: Int, z: Int) {
    val world = player.serverWorld
    if (!world.chunkManager.isChunkLoaded(x shr 4, z shr 4)) return
    pendingPillarPositions.get().add(x to z)
}

fun commitPillars(player: ServerPlayerEntity) {
    val world = player.serverWorld
    val bottom = world.bottomY
    val top = world.topY
    pendingPillarPositions.get().forEach { (x, z) ->
        var y = bottom
        while (y <= top) {
            world.spawnParticles(player, PILLAR_PARTICLE, true, x + 0.5, y.toDouble(), z + 0.5, 2, 0.0, 0.0, 0.0, 0.0)
            y += PILLAR_STEP
        }
    }
    pendingPillarPositions.get().clear()
}

fun clearSelectionDisplay(@Suppress("UNUSED_PARAMETER") player: ServerPlayerEntity) = Unit
