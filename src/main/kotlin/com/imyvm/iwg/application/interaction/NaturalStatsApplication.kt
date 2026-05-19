package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

private const val SUMMARY_ITEM_LIMIT = 8
private const val DETAIL_ITEM_LIMIT = 20

fun onQueryRegionNaturalStats(player: ServerPlayer, region: Region, categoryName: String?, isApi: Boolean): Int {
    val category = NaturalStatsCategory.fromName(categoryName)
    if (category == null) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.stats.error.invalid_category",
                categoryName,
                NaturalStatsCategory.entries.joinToString(", ") { it.commandName }
            )!!
        )
        return 0
    }

    return onQueryRegionNaturalStats(player, region, category, isApi)
}

fun onQueryRegionNaturalStats(player: ServerPlayer, region: Region, category: NaturalStatsCategory, isApi: Boolean): Int {
    val result = RegionNaturalStatsCollector.collectRegionStats(player.level().server, region)
    return when (result) {
        is RegionNaturalStatsResult.ChunkLimitExceeded -> {
            player.sendSystemMessage(
                Translator.tr(
                    "interaction.meta.stats.error.chunk_limit",
                    result.dimensionId,
                    result.candidateChunkCount,
                    result.limit
                )!!
            )
            0
        }

        is RegionNaturalStatsResult.DimensionUnavailable -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.stats.error.dimension_unavailable", result.dimensionId)!!)
            0
        }

        is RegionNaturalStatsResult.Success -> sendStatsMessages(player, region, result.stats, category, isApi)
    }
}

private fun sendStatsMessages(
    player: ServerPlayer,
    region: Region,
    stats: RegionNaturalStats,
    category: NaturalStatsCategory,
    isApi: Boolean
): Int {
    if (stats.dimensionStats.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.stats.empty", region.name)!!)
        return 0
    }

    val headerKey = if (isApi) "interaction.meta.api.stats.header" else "interaction.meta.command.stats.header"
    player.sendSystemMessage(
        Translator.tr(
            headerKey,
            region.name,
            categoryLabel(category),
            stats.loadedChunkCount,
            stats.candidateChunkCount,
            stats.sampledColumnCount,
            stats.scopeCount
        )!!
    )

    buildCategoryLines(stats, category, isDetail = false).forEach(player::sendSystemMessage)

    if (stats.dimensionStats.size > 1) {
        stats.dimensionStats.forEach { (dimensionId, dimensionStats) ->
            player.sendSystemMessage(
                Translator.tr(
                    "interaction.meta.stats.dimension.header",
                    dimensionId,
                    dimensionStats.loadedChunkCount,
                    dimensionStats.candidateChunkCount,
                    dimensionStats.sampledColumnCount,
                    dimensionStats.scopeCount
                )!!
            )
            buildCategoryLines(dimensionStats, category, isDetail = true).forEach(player::sendSystemMessage)
        }
    }

    if (stats.isPartial) {
        player.sendSystemMessage(Translator.tr("interaction.meta.stats.partial")!!)
    }

    return 1
}

private fun buildCategoryLines(
    stats: RegionNaturalStats,
    category: NaturalStatsCategory,
    isDetail: Boolean
): List<Component> {
    val itemLimit = if (isDetail) DETAIL_ITEM_LIMIT else SUMMARY_ITEM_LIMIT
    return when (category) {
        NaturalStatsCategory.ALL -> listOf(
            Translator.tr("interaction.meta.stats.line.difficulty", formatDifficulty(stats.averageLocalDifficulty))!!,
            Translator.tr("interaction.meta.stats.line.structures", formatCountMap(stats.structureCounts, itemLimit))!!,
            Translator.tr(
                "interaction.meta.stats.line.surface",
                formatDistributionMap(stats.surfaceBlockCounts, stats.sampledColumnCount, itemLimit)
            )!!,
            Translator.tr(
                "interaction.meta.stats.line.biomes",
                formatDistributionMap(stats.biomeCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )

        NaturalStatsCategory.STRUCTURES -> listOf(
            Translator.tr("interaction.meta.stats.line.structures", formatCountMap(stats.structureCounts, itemLimit))!!
        )

        NaturalStatsCategory.DIFFICULTY -> listOf(
            Translator.tr("interaction.meta.stats.line.difficulty", formatDifficulty(stats.averageLocalDifficulty))!!
        )

        NaturalStatsCategory.SURFACE -> listOf(
            Translator.tr(
                "interaction.meta.stats.line.surface",
                formatDistributionMap(stats.surfaceBlockCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )

        NaturalStatsCategory.BIOMES -> listOf(
            Translator.tr(
                "interaction.meta.stats.line.biomes",
                formatDistributionMap(stats.biomeCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )
    }
}

private fun buildCategoryLines(
    stats: DimensionNaturalStats,
    category: NaturalStatsCategory,
    isDetail: Boolean
): List<Component> {
    val itemLimit = if (isDetail) DETAIL_ITEM_LIMIT else SUMMARY_ITEM_LIMIT
    return when (category) {
        NaturalStatsCategory.ALL -> listOf(
            Translator.tr("interaction.meta.stats.line.difficulty", formatDifficulty(stats.averageLocalDifficulty))!!,
            Translator.tr("interaction.meta.stats.line.structures", formatCountMap(stats.structureCounts, itemLimit))!!,
            Translator.tr(
                "interaction.meta.stats.line.surface",
                formatDistributionMap(stats.surfaceBlockCounts, stats.sampledColumnCount, itemLimit)
            )!!,
            Translator.tr(
                "interaction.meta.stats.line.biomes",
                formatDistributionMap(stats.biomeCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )

        NaturalStatsCategory.STRUCTURES -> listOf(
            Translator.tr("interaction.meta.stats.line.structures", formatCountMap(stats.structureCounts, itemLimit))!!
        )

        NaturalStatsCategory.DIFFICULTY -> listOf(
            Translator.tr("interaction.meta.stats.line.difficulty", formatDifficulty(stats.averageLocalDifficulty))!!
        )

        NaturalStatsCategory.SURFACE -> listOf(
            Translator.tr(
                "interaction.meta.stats.line.surface",
                formatDistributionMap(stats.surfaceBlockCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )

        NaturalStatsCategory.BIOMES -> listOf(
            Translator.tr(
                "interaction.meta.stats.line.biomes",
                formatDistributionMap(stats.biomeCounts, stats.sampledColumnCount, itemLimit)
            )!!
        )
    }
}

private fun categoryLabel(category: NaturalStatsCategory): String =
    Translator.raw("interaction.meta.stats.category.${category.translationSuffix}") ?: category.commandName

private fun formatDifficulty(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.3f", it) }
        ?: (Translator.raw("interaction.meta.stats.not_available") ?: "N/A")

private fun formatCountMap(values: Map<Identifier, Int>, limit: Int): String {
    if (values.isEmpty()) {
        return Translator.raw("interaction.meta.stats.none") ?: "none"
    }

    val visible = values.entries.take(limit)
    val body = visible.joinToString(", ") { "${it.key} x${it.value}" }
    val remaining = values.size - visible.size
    return if (remaining > 0) {
        "$body${Translator.raw("interaction.meta.stats.more_suffix", remaining) ?: ""}"
    } else {
        body
    }
}

private fun formatDistributionMap(values: Map<Identifier, Int>, total: Int, limit: Int): String {
    if (values.isEmpty() || total <= 0) {
        return Translator.raw("interaction.meta.stats.none") ?: "none"
    }

    val visible = values.entries.take(limit)
    val body = visible.joinToString(", ") { entry ->
        val percentage = entry.value.toDouble() * 100.0 / total
        "${entry.key} ${String.format(Locale.ROOT, "%.1f%%", percentage)} (${entry.value})"
    }
    val remaining = values.size - visible.size
    return if (remaining > 0) {
        "$body${Translator.raw("interaction.meta.stats.more_suffix", remaining) ?: ""}"
    } else {
        body
    }
}
