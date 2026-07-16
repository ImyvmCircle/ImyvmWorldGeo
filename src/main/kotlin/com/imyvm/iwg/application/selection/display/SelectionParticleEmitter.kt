package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_LINE_STEP
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.ceil
import kotlin.math.hypot

private val OLD_SCOPE_PARTICLE = DustParticleOptions((255 shl 24) or (51 shl 16) or (153 shl 8) or 255, 2.5f)
private val MODIFYING_SCOPE_PARTICLE = DustParticleOptions((255 shl 24) or (255 shl 16) or (140 shl 8) or 0, 2.5f)

internal fun emitLineSurface(
    player: ServerPlayer,
    pos1: BlockPos,
    pos2: BlockPos,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, pos1.x, pos1.z, pos2.x, pos2.z, ParticleTypes.LAVA, session, maxSamples)

internal fun emitLineSurface(
    player: ServerPlayer,
    x1: Int,
    z1: Int,
    x2: Int,
    z2: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, x1, z1, x2, z2, ParticleTypes.LAVA, session, maxSamples)

internal fun emitLineSurfaceOld(
    player: ServerPlayer,
    pos1: BlockPos,
    pos2: BlockPos,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, pos1.x, pos1.z, pos2.x, pos2.z, OLD_SCOPE_PARTICLE, session, maxSamples)

internal fun emitLineSurfaceOld(
    player: ServerPlayer,
    x1: Int,
    z1: Int,
    x2: Int,
    z2: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, x1, z1, x2, z2, OLD_SCOPE_PARTICLE, session, maxSamples)

internal fun emitLineSurfaceModifying(
    player: ServerPlayer,
    pos1: BlockPos,
    pos2: BlockPos,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, pos1.x, pos1.z, pos2.x, pos2.z, MODIFYING_SCOPE_PARTICLE, session, maxSamples)

internal fun emitLineSurfaceModifying(
    player: ServerPlayer,
    x1: Int,
    z1: Int,
    x2: Int,
    z2: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = emitLineSurface(player, x1, z1, x2, z2, MODIFYING_SCOPE_PARTICLE, session, maxSamples)

internal fun drawCircleOutline(
    player: ServerPlayer,
    centerX: Int,
    centerZ: Int,
    radius: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = drawCircleOutline(player, centerX, centerZ, radius, ParticleTypes.LAVA, session, maxSamples)

internal fun drawCircleOutlineOld(
    player: ServerPlayer,
    centerX: Int,
    centerZ: Int,
    radius: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = drawCircleOutline(player, centerX, centerZ, radius, OLD_SCOPE_PARTICLE, session, maxSamples)

internal fun drawCircleOutlineModifying(
    player: ServerPlayer,
    centerX: Int,
    centerZ: Int,
    radius: Int,
    session: SelectionDisplaySession,
    maxSamples: Int = session.surfaceUnits
) = drawCircleOutline(player, centerX, centerZ, radius, MODIFYING_SCOPE_PARTICLE, session, maxSamples)

private fun emitLineSurface(
    player: ServerPlayer,
    x1: Int,
    z1: Int,
    x2: Int,
    z2: Int,
    particle: ParticleOptions,
    session: SelectionDisplaySession,
    maxSamples: Int
) {
    val world = player.level()
    sampleLineCoordinates(
        x1,
        z1,
        x2,
        z2,
        SELECTION_DISPLAY_LINE_STEP.value,
        minOf(maxSamples, session.surfaceUnits)
    ) { x, z ->
        if (!session.tryUseSurface()) return@sampleLineCoordinates
        if (!world.chunkSource.hasChunk(x shr 4, z shr 4)) return@sampleLineCoordinates
        val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        world.sendParticles(player, particle, true, false, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
    }
}

private fun drawCircleOutline(
    player: ServerPlayer,
    centerX: Int,
    centerZ: Int,
    radius: Int,
    particle: ParticleOptions,
    session: SelectionDisplaySession,
    maxSamples: Int
) {
    val world = player.level()
    sampleCircleCoordinates(
        centerX,
        centerZ,
        radius,
        SELECTION_DISPLAY_LINE_STEP.value,
        minOf(maxSamples, session.surfaceUnits)
    ) { x, z ->
        if (!session.tryUseSurface()) return@sampleCircleCoordinates
        if (!world.chunkSource.hasChunk(x shr 4, z shr 4)) return@sampleCircleCoordinates
        val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        world.sendParticles(player, particle, true, false, x + 0.5, y.toDouble(), z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
    }
}

internal inline fun sampleLineCoordinates(
    x1: Int,
    z1: Int,
    x2: Int,
    z2: Int,
    step: Int,
    maxSamples: Int,
    emit: (Int, Int) -> Unit
): Int {
    require(step > 0) { "line step must be positive" }
    if (maxSamples <= 0 || x1 == x2 && z1 == z2) return 0
    val dx = x2.toLong() - x1
    val dz = z2.toLong() - z1
    val naturalSamples = maxOf(2L, ceil(hypot(dx.toDouble(), dz.toDouble()) / step).toLong() + 1L)
    val sampleCount = minOf(naturalSamples, maxSamples.toLong()).toInt()
    for (index in 0 until sampleCount) {
        val fraction = if (sampleCount == 1) 0.5 else index.toDouble() / (sampleCount - 1)
        val x = when {
            sampleCount == 1 -> (x1 + dx * fraction).toInt()
            index == 0 -> x1
            index == sampleCount - 1 -> x2
            else -> (x1 + dx * fraction).toInt()
        }
        val z = when {
            sampleCount == 1 -> (z1 + dz * fraction).toInt()
            index == 0 -> z1
            index == sampleCount - 1 -> z2
            else -> (z1 + dz * fraction).toInt()
        }
        emit(x, z)
    }
    return sampleCount
}

internal inline fun sampleCircleCoordinates(
    centerX: Int,
    centerZ: Int,
    radius: Int,
    step: Int,
    maxSamples: Int,
    emit: (Int, Int) -> Unit
): Int {
    require(radius >= 0) { "circle radius must not be negative" }
    require(step > 0) { "circle step must be positive" }
    if (maxSamples <= 0) return 0
    val naturalSamples = maxOf(8L, ceil(2.0 * Math.PI * radius / step).toLong())
    val sampleCount = minOf(naturalSamples, maxSamples.toLong()).toInt()
    for (index in 0 until sampleCount) {
        val angle = 2.0 * Math.PI * index / sampleCount
        emit(
            (centerX + radius * Math.cos(angle)).toInt(),
            (centerZ + radius * Math.sin(angle)).toInt()
        )
    }
    return sampleCount
}
