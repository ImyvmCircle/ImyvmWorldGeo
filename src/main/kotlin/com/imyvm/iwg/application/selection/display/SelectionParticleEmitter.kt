package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_LINE_STEP
import net.minecraft.particle.DustParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import org.joml.Vector3f
import kotlin.math.sqrt

private val OLD_SCOPE_PARTICLE = DustParticleEffect(Vector3f(0.2f, 0.6f, 1.0f), 2.5f)
private val MODIFYING_SCOPE_PARTICLE = DustParticleEffect(Vector3f(1.0f, 0.55f, 0.0f), 2.5f)

fun emitLineSurface(player: ServerPlayerEntity, pos1: BlockPos, pos2: BlockPos) {
    val world = player.serverWorld
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
            world.spawnParticles(player, ParticleTypes.LAVA, true, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun emitLineSurfaceOld(player: ServerPlayerEntity, pos1: BlockPos, pos2: BlockPos) {
    val world = player.serverWorld
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
            world.spawnParticles(player, OLD_SCOPE_PARTICLE, true, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun drawCircleOutline(player: ServerPlayerEntity, centerX: Int, centerZ: Int, radius: Int) {
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
        emitLineSurface(player, samples[i], samples[(i + 1) % samples.size])
    }
}

fun drawCircleOutlineOld(player: ServerPlayerEntity, centerX: Int, centerZ: Int, radius: Int) {
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
        emitLineSurfaceOld(player, samples[i], samples[(i + 1) % samples.size])
    }
}

fun emitLineSurfaceModifying(player: ServerPlayerEntity, pos1: BlockPos, pos2: BlockPos) {
    val world = player.serverWorld
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
            world.spawnParticles(player, MODIFYING_SCOPE_PARTICLE, true, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun drawCircleOutlineModifying(player: ServerPlayerEntity, centerX: Int, centerZ: Int, radius: Int) {
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
        emitLineSurfaceModifying(player, samples[i], samples[(i + 1) % samples.size])
    }
}
