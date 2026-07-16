package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.infra.config.SelectionConfig.SELECTION_DISPLAY_PILLAR_STEP
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerPlayer

private val PILLAR_PARTICLE = DustParticleOptions(-1, 4.0f)

internal fun emitPillar(player: ServerPlayer, x: Int, z: Int, session: SelectionDisplaySession) {
    if (!session.queuePillar(x, z)) return
    val world = player.level()
    if (!world.chunkSource.hasChunk(x shr 4, z shr 4)) {
        session.removePillar(x, z)
    }
}

internal fun commitPillars(player: ServerPlayer, session: SelectionDisplaySession) {
    val world = player.level()
    var index = 0
    session.forEachPillar { x, z ->
        val pillarsLeft = session.pillarCount - index++
        if (session.exhausted) return@forEachPillar
        val maxSamples = maxOf(1, session.remainingUnits / pillarsLeft)
        sampleVerticalCoordinates(
            world.minY,
            world.maxY,
            SELECTION_DISPLAY_PILLAR_STEP.value,
            maxSamples
        ) { y ->
            if (!session.tryUse()) return@sampleVerticalCoordinates
            world.sendParticles(player, PILLAR_PARTICLE, true, false, x + 0.5, y.toDouble(), z + 0.5, 2, 0.0, 0.0, 0.0, 0.0)
        }
    }
    session.clearPillars()
}

internal inline fun sampleVerticalCoordinates(
    bottom: Int,
    top: Int,
    step: Int,
    maxSamples: Int,
    emit: (Int) -> Unit
): Int {
    require(bottom <= top) { "pillar bounds are inverted" }
    require(step > 0) { "pillar step must be positive" }
    if (maxSamples <= 0) return 0
    val naturalSamples = (top.toLong() - bottom) / step + 1L
    val sampleCount = minOf(naturalSamples, maxSamples.toLong()).toInt()
    for (index in 0 until sampleCount) {
        val y = when {
            sampleCount == 1 -> ((bottom.toLong() + top) / 2L).toInt()
            index == 0 -> bottom
            index == sampleCount - 1 -> top
            else -> (bottom + (top.toLong() - bottom) * index / (sampleCount - 1)).toInt()
        }
        emit(y)
    }
    return sampleCount
}

fun clearSelectionDisplay(@Suppress("UNUSED_PARAMETER") player: ServerPlayer) = Unit
