package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.domain.RegionPlayerStats
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

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
            )
        )
        return 0
    }

    return onQueryRegionNaturalStats(player, region, category, isApi)
}

fun onQueryRegionNaturalStats(player: ServerPlayer, region: Region, category: NaturalStatsCategory, isApi: Boolean): Int {
    if (category == NaturalStatsCategory.PLAYERS) {
        return onQueryRegionPlayerStats(player, region, isApi)
    }

    val result = RegionNaturalStatsCollector.collectRegionStats(player.level().server, region)
    return when (result) {
        is RegionNaturalStatsResult.ChunkLimitExceeded -> {
            player.sendSystemMessage(
                Translator.tr(
                    "interaction.meta.stats.error.chunk_limit",
                    result.dimensionId,
                    result.candidateChunkCount,
                    result.limit
                )
            )
            0
        }

        is RegionNaturalStatsResult.WorkLimitExceeded -> {
            player.sendSystemMessage(
                Translator.tr(
                    "interaction.meta.stats.error.work_limit",
                    result.dimensionId,
                    result.requestedWorkUnits,
                    result.limit
                )
            )
            0
        }

        is RegionNaturalStatsResult.DimensionUnavailable -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.stats.error.dimension_unavailable", result.dimensionId))
            0
        }

        is RegionNaturalStatsResult.Success -> sendStatsMessages(player, region, result.stats, category, isApi)
    }
}

fun onQueryRegionPlayerStats(player: ServerPlayer, region: Region, isApi: Boolean): Int =
    sendPlayerStatsMessages(player, region, RegionDatabase.getRegionPlayerStats(region), isApi)

private fun sendStatsMessages(
    player: ServerPlayer,
    region: Region,
    stats: RegionNaturalStats,
    category: NaturalStatsCategory,
    isApi: Boolean
): Int {
    if (stats.dimensionStats.isEmpty()) {
        player.sendSystemMessage(Translator.tr("interaction.meta.stats.empty", region.name))
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
        )
    )

    buildNaturalStatsCategoryLines(
        NaturalStatsLineValues.from(stats),
        category,
        SUMMARY_ITEM_LIMIT
    ).forEach(player::sendSystemMessage)

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
                )
            )
            buildNaturalStatsCategoryLines(
                NaturalStatsLineValues.from(dimensionStats),
                category,
                DETAIL_ITEM_LIMIT
            ).forEach(player::sendSystemMessage)
        }
    }

    if (stats.isPartial) {
        player.sendSystemMessage(Translator.tr("interaction.meta.stats.partial"))
    }

    return 1
}

private fun sendPlayerStatsMessages(
    player: ServerPlayer,
    region: Region,
    stats: RegionPlayerStats,
    isApi: Boolean
): Int {
    if (stats.isEmpty) {
        player.sendSystemMessage(Translator.tr("interaction.meta.player_stats.empty", region.name))
        return 0
    }

    val headerKey = if (isApi) "interaction.meta.api.player_stats.header" else "interaction.meta.command.player_stats.header"
    player.sendSystemMessage(
        Translator.tr(
            headerKey,
            region.name,
            categoryLabel(NaturalStatsCategory.PLAYERS),
            stats.trackedPlayerCount
        )
    )

    listOf(
        Translator.tr("interaction.meta.player_stats.line.entries", stats.entryCount),
        Translator.tr("interaction.meta.player_stats.line.stay", formatDuration(stats.stayMillis)),
        Translator.tr("interaction.meta.player_stats.line.deaths", stats.deathCount),
        Translator.tr("interaction.meta.player_stats.line.block_places", stats.blockPlaceCount),
        Translator.tr("interaction.meta.player_stats.line.block_breaks", stats.blockBreakCount)
    ).forEach(player::sendSystemMessage)

    return 1
}

private fun categoryLabel(category: NaturalStatsCategory): String =
    Translator.raw(category.translationKey)

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    val parts = buildList {
        if (days > 0) add(Translator.raw("interaction.meta.player_stats.duration.day", days))
        if (hours > 0) add(Translator.raw("interaction.meta.player_stats.duration.hour", hours))
        if (minutes > 0) add(Translator.raw("interaction.meta.player_stats.duration.minute", minutes))
        if (seconds > 0 || isEmpty()) {
            add(Translator.raw("interaction.meta.player_stats.duration.second", seconds))
        }
    }
    return parts.joinToString(" ")
}
