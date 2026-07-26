package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.Locale

internal data class NaturalStatsLineValues(
    val sampledColumnCount: Int,
    val averageLocalDifficulty: Double?,
    val structureCounts: Map<Identifier, Int>,
    val surfaceBlockCounts: Map<Identifier, Int>,
    val biomeCounts: Map<Identifier, Int>
) {
    companion object {
        fun from(stats: RegionNaturalStats) = NaturalStatsLineValues(
            stats.sampledColumnCount,
            stats.averageLocalDifficulty,
            stats.structureCounts,
            stats.surfaceBlockCounts,
            stats.biomeCounts
        )

        fun from(stats: DimensionNaturalStats) = NaturalStatsLineValues(
            stats.sampledColumnCount,
            stats.averageLocalDifficulty,
            stats.structureCounts,
            stats.surfaceBlockCounts,
            stats.biomeCounts
        )
    }
}

internal fun buildNaturalStatsCategoryLines(
    values: NaturalStatsLineValues,
    category: NaturalStatsCategory,
    itemLimit: Int
): List<Component> {
    return when (category) {
        NaturalStatsCategory.ALL -> listOf(
            difficultyLine(values),
            structuresLine(values, itemLimit),
            surfaceLine(values, itemLimit),
            biomesLine(values, itemLimit)
        )
        NaturalStatsCategory.STRUCTURES -> listOf(structuresLine(values, itemLimit))
        NaturalStatsCategory.DIFFICULTY -> listOf(difficultyLine(values))
        NaturalStatsCategory.SURFACE -> listOf(surfaceLine(values, itemLimit))
        NaturalStatsCategory.BIOMES -> listOf(biomesLine(values, itemLimit))
        NaturalStatsCategory.PLAYERS -> emptyList()
    }
}

private fun difficultyLine(values: NaturalStatsLineValues): Component =
    Translator.tr("interaction.meta.stats.line.difficulty", formatDifficulty(values.averageLocalDifficulty))

private fun structuresLine(values: NaturalStatsLineValues, itemLimit: Int): Component =
    Translator.tr("interaction.meta.stats.line.structures", formatCountMap(values.structureCounts, itemLimit))

private fun surfaceLine(values: NaturalStatsLineValues, itemLimit: Int): Component =
    Translator.tr(
        "interaction.meta.stats.line.surface",
        formatDistributionMap(values.surfaceBlockCounts, values.sampledColumnCount, itemLimit)
    )

private fun biomesLine(values: NaturalStatsLineValues, itemLimit: Int): Component =
    Translator.tr(
        "interaction.meta.stats.line.biomes",
        formatDistributionMap(values.biomeCounts, values.sampledColumnCount, itemLimit)
    )

private fun formatDifficulty(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.3f", it) }
        ?: Translator.raw("interaction.meta.stats.not_available")

private fun formatCountMap(values: Map<Identifier, Int>, limit: Int): String {
    if (values.isEmpty()) return Translator.raw("interaction.meta.stats.none")

    val visible = values.entries.take(limit)
    val body = visible.joinToString(", ") { "${it.key} x${it.value}" }
    val remaining = values.size - visible.size
    return if (remaining > 0) {
        "$body${Translator.raw("interaction.meta.stats.more_suffix", remaining)}"
    } else {
        body
    }
}

private fun formatDistributionMap(values: Map<Identifier, Int>, total: Int, limit: Int): String {
    if (values.isEmpty() || total <= 0) return Translator.raw("interaction.meta.stats.none")

    val visible = values.entries.take(limit)
    val body = visible.joinToString(", ") { entry ->
        val percentage = entry.value.toDouble() * 100.0 / total
        "${entry.key} ${String.format(Locale.ROOT, "%.1f%%", percentage)} (${entry.value})"
    }
    val remaining = values.size - visible.size
    return if (remaining > 0) {
        "$body${Translator.raw("interaction.meta.stats.more_suffix", remaining)}"
    } else {
        body
    }
}
