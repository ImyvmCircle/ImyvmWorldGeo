package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_LINE_STEP
import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_PILLAR_STEP
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import kotlin.math.sqrt

fun emitBeaconPillar(world: ServerWorld, x: Int, z: Int) {
    if (!world.chunkManager.isChunkLoaded(x shr 4, z shr 4)) return
    val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
    val step = SELECTION_DISPLAY_PILLAR_STEP.value
    var y = world.bottomY
    while (y <= topY) {
        world.spawnParticles(ParticleTypes.END_ROD, x.toDouble(), y.toDouble(), z.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
        y += step
    }
    world.spawnParticles(ParticleTypes.END_ROD, x.toDouble(), topY.toDouble() + 1, z.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
}

fun emitLineSurface(world: ServerWorld, pos1: BlockPos, pos2: BlockPos) {
    val dx = (pos2.x - pos1.x).toDouble()
    val dz = (pos2.z - pos1.z).toDouble()
    val lineLength = sqrt(dx * dx + dz * dz)
    if (lineLength == 0.0) return
    val step = SELECTION_DISPLAY_LINE_STEP.value
    val stepFraction = step / lineLength
    var t = 0.0
    while (t <= 1.0) {
        val x = (pos1.x + dx * t).toInt()
        val z = (pos1.z + dz * t).toInt()
        if (world.chunkManager.isChunkLoaded(x shr 4, z shr 4)) {
            val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            world.spawnParticles(ParticleTypes.CRIT, x.toDouble(), y.toDouble(), z.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun drawCircleOutline(world: ServerWorld, centerX: Int, centerZ: Int, radius: Int) {
    val numSamples = maxOf(8, (2 * Math.PI * radius / SELECTION_DISPLAY_LINE_STEP.value).toInt())
    val samples = (0 until numSamples).map { i ->
        val angle = 2 * Math.PI * i / numSamples
        BlockPos(
            (centerX + radius * Math.cos(angle)).toInt(),
            0,
            (centerZ + radius * Math.sin(angle)).toInt()
        )
    }
    for (i in samples.indices) {
        emitLineSurface(world, samples[i], samples[(i + 1) % samples.size])
    }
}
