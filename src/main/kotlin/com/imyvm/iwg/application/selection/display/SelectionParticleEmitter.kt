package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_LINE_STEP
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.sqrt

// ARGB colors: blue-ish (0.2, 0.6, 1.0) and orange (1.0, 0.55, 0.0)
private val OLD_SCOPE_PARTICLE = DustParticleOptions((255 shl 24) or (51 shl 16) or (153 shl 8) or 255, 2.5f)
private val MODIFYING_SCOPE_PARTICLE = DustParticleOptions((255 shl 24) or (255 shl 16) or (140 shl 8) or 0, 2.5f)

fun emitLineSurface(player: ServerPlayer, pos1: BlockPos, pos2: BlockPos) {
    val world = player.level()
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
        if (world.chunkSource.hasChunk(x shr 4, z shr 4)) {
            val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            world.sendParticles(player, ParticleTypes.LAVA, true, false, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun emitLineSurfaceOld(player: ServerPlayer, pos1: BlockPos, pos2: BlockPos) {
    val world = player.level()
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
        if (world.chunkSource.hasChunk(x shr 4, z shr 4)) {
            val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            world.sendParticles(player, OLD_SCOPE_PARTICLE, true, false, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun drawCircleOutline(player: ServerPlayer, centerX: Int, centerZ: Int, radius: Int) {
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

fun drawCircleOutlineOld(player: ServerPlayer, centerX: Int, centerZ: Int, radius: Int) {
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

fun emitLineSurfaceModifying(player: ServerPlayer, pos1: BlockPos, pos2: BlockPos) {
    val world = player.level()
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
        if (world.chunkSource.hasChunk(x shr 4, z shr 4)) {
            val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            world.sendParticles(player, MODIFYING_SCOPE_PARTICLE, true, false, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        t += stepFraction
    }
}

fun drawCircleOutlineModifying(player: ServerPlayer, centerX: Int, centerZ: Int, radius: Int) {
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