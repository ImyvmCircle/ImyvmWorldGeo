package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.RegionNaturalStatsCollector
import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.domain.RegionPlayerStats
import com.imyvm.iwg.domain.WorldGeoSpaceType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

private const val SUMMARY_ITEM_LIMIT = 8
private const val DETAIL_ITEM_LIMIT = 20
private const val DEBUG_GEOGRAPHY_PAGE_SIZE = 5

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

fun onQueryRegionPlayerStats(player: ServerPlayer, region: Region, isApi: Boolean): Int =
    sendPlayerStatsMessages(player, region, RegionDatabase.getRegionPlayerStats(region), isApi)

fun onDebugGeographyRegion(player: ServerPlayer, region: Region): Int {
    RegionDatabase.requireCanonicalRegion(region)
    return sendDebugGeographyOverview(
        player,
        WorldGeoSpaceType.REGION.name,
        region.numberID.toString(),
        region.name,
        region.calculateTotalArea(),
        RegionNaturalStatsCollector.collectRegionStats(player.level().server, region),
        "/imyvmWorldGeo debug geography region ${region.numberID} detail"
    )
}

fun onDebugGeographyRegionDetail(player: ServerPlayer, region: Region, pageRaw: String?): Int {
    RegionDatabase.requireCanonicalRegion(region)
    return sendDebugGeographyDetail(
        player,
        WorldGeoSpaceType.REGION.name,
        region.numberID.toString(),
        RegionNaturalStatsCollector.collectRegionStats(player.level().server, region),
        pageRaw
    )
}

fun onDebugGeographyScope(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    RegionDatabase.requireCanonicalScope(region, scope)
    return sendDebugGeographyOverview(
        player,
        WorldGeoSpaceType.GEOSCOPE.name,
        scope.requireAssignedScopeId().raw.toString(),
        scope.scopeName,
        scope.geoShape?.calculateArea(),
        RegionNaturalStatsCollector.collectScopeStats(player.level().server, scope),
        "/imyvmWorldGeo debug geography scope ${region.numberID} ${scope.scopeName} detail"
    )
}

fun onDebugGeographyScopeDetail(player: ServerPlayer, region: Region, scope: GeoScope, pageRaw: String?): Int {
    RegionDatabase.requireCanonicalScope(region, scope)
    return sendDebugGeographyDetail(
        player,
        WorldGeoSpaceType.GEOSCOPE.name,
        scope.requireAssignedScopeId().raw.toString(),
        RegionNaturalStatsCollector.collectScopeStats(player.level().server, scope),
        pageRaw
    )
}

fun onDebugGeographySubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    return sendDebugGeographyOverview(
        player,
        WorldGeoSpaceType.SUBSPACE.name,
        subSpace.subSpaceId.toString(),
        subSpace.name,
        subSpace.geoShape.calculateArea(),
        RegionNaturalStatsCollector.collectSubSpaceStats(player.level().server, subSpace),
        "/imyvmWorldGeo debug geography subspace ${region.numberID} ${subSpace.name} detail"
    )
}

fun onDebugGeographySubSpaceDetail(
    player: ServerPlayer,
    region: Region,
    parentScope: GeoScope,
    subSpace: SubSpace,
    pageRaw: String?
): Int {
    RegionDatabase.requireCanonicalSubSpace(region, parentScope, subSpace)
    return sendDebugGeographyDetail(
        player,
        WorldGeoSpaceType.SUBSPACE.name,
        subSpace.subSpaceId.toString(),
        RegionNaturalStatsCollector.collectSubSpaceStats(player.level().server, subSpace),
        pageRaw
    )
}

private fun sendDebugGeographyOverview(
    player: ServerPlayer,
    type: String,
    id: String,
    name: String,
    area: Double?,
    result: RegionNaturalStatsResult,
    detailCommand: String
): Int = when (result) {
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

    is RegionNaturalStatsResult.Success -> {
        val stats = result.stats
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.debug.geography.header",
                type,
                id,
                name,
                stats.dimensionStats.keys.joinToString(", ").ifBlank { "-" },
                area?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "-",
                dominantBiome(stats)?.toString() ?: "-",
                stats.sampledColumnCount,
                stats.loadedChunkCount,
                stats.candidateChunkCount,
                stats.isPartial
            )!!
        )
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.debug.geography.summary",
                formatDistributionMap(stats.biomeCounts, stats.sampledColumnCount, SUMMARY_ITEM_LIMIT),
                formatDistributionMap(stats.surfaceBlockCounts, stats.sampledColumnCount, SUMMARY_ITEM_LIMIT),
                formatCountMap(stats.structureCounts, SUMMARY_ITEM_LIMIT),
                formatDifficulty(stats.averageLocalDifficulty)
            )!!
        )
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.geography.detail_hint", detailCommand)!!)
        1
    }
}

private fun sendDebugGeographyDetail(
    player: ServerPlayer,
    type: String,
    id: String,
    result: RegionNaturalStatsResult,
    pageRaw: String?
): Int = when (result) {
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

    is RegionNaturalStatsResult.Success -> {
        val lines = buildDebugGeographyDetailLines(result.stats)
        if (lines.isEmpty()) {
            player.sendSystemMessage(Translator.tr("interaction.meta.debug.geography.detail.empty", type, id)!!)
            return 0
        }
        val totalPages = ((lines.size + DEBUG_GEOGRAPHY_PAGE_SIZE - 1) / DEBUG_GEOGRAPHY_PAGE_SIZE).coerceAtLeast(1)
        val page = (pageRaw?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
        player.sendSystemMessage(Translator.tr("interaction.meta.debug.geography.detail.header", type, id, page, totalPages, lines.size)!!)
        lines.drop((page - 1) * DEBUG_GEOGRAPHY_PAGE_SIZE).take(DEBUG_GEOGRAPHY_PAGE_SIZE).forEach(player::sendSystemMessage)
        lines.size
    }
}

private fun buildDebugGeographyDetailLines(stats: RegionNaturalStats): List<Component> = buildList {
    stats.dimensionStats.forEach { (dimensionId, dimensionStats) ->
        add(
            Translator.tr(
                "interaction.meta.debug.geography.detail.dimension",
                dimensionId,
                dimensionStats.sampledColumnCount,
                dimensionStats.loadedChunkCount,
                dimensionStats.candidateChunkCount,
                formatDifficulty(dimensionStats.averageLocalDifficulty)
            )!!
        )
    }
    addDebugDistributionLines("biome", stats.biomeCounts, stats.sampledColumnCount)
    addDebugDistributionLines("surface", stats.surfaceBlockCounts, stats.sampledColumnCount)
    stats.structureCounts.forEach { (structureId, count) ->
        add(Translator.tr("interaction.meta.debug.geography.detail.count", "structure", structureId, count)!!)
    }
}

private fun MutableList<Component>.addDebugDistributionLines(label: String, values: Map<Identifier, Int>, total: Int) {
    values.forEach { (key, count) ->
        val percent = if (total <= 0) "0.0%" else String.format(Locale.ROOT, "%.1f%%", count.toDouble() * 100.0 / total)
        add(Translator.tr("interaction.meta.debug.geography.detail.distribution", label, key, count, percent)!!)
    }
}

private fun dominantBiome(stats: RegionNaturalStats): Identifier? =
    stats.biomeCounts.maxByOrNull { it.value }?.key

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

        NaturalStatsCategory.PLAYERS -> emptyList()
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

        NaturalStatsCategory.PLAYERS -> emptyList()
    }
}

private fun sendPlayerStatsMessages(
    player: ServerPlayer,
    region: Region,
    stats: RegionPlayerStats,
    isApi: Boolean
): Int {
    if (stats.isEmpty) {
        player.sendSystemMessage(Translator.tr("interaction.meta.player_stats.empty", region.name)!!)
        return 0
    }

    val headerKey = if (isApi) "interaction.meta.api.player_stats.header" else "interaction.meta.command.player_stats.header"
    player.sendSystemMessage(
        Translator.tr(
            headerKey,
            region.name,
            categoryLabel(NaturalStatsCategory.PLAYERS),
            stats.trackedPlayerCount
        )!!
    )

    listOf(
        Translator.tr("interaction.meta.player_stats.line.entries", stats.entryCount)!!,
        Translator.tr("interaction.meta.player_stats.line.stay", formatDuration(stats.stayMillis))!!,
        Translator.tr("interaction.meta.player_stats.line.deaths", stats.deathCount)!!,
        Translator.tr("interaction.meta.player_stats.line.block_places", stats.blockPlaceCount)!!,
        Translator.tr("interaction.meta.player_stats.line.block_breaks", stats.blockBreakCount)!!
    ).forEach(player::sendSystemMessage)

    return 1
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

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    val parts = buildList {
        if (days > 0) add(Translator.raw("interaction.meta.player_stats.duration.day", days) ?: "${days}d")
        if (hours > 0) add(Translator.raw("interaction.meta.player_stats.duration.hour", hours) ?: "${hours}h")
        if (minutes > 0) add(Translator.raw("interaction.meta.player_stats.duration.minute", minutes) ?: "${minutes}m")
        if (seconds > 0 || isEmpty()) {
            add(Translator.raw("interaction.meta.player_stats.duration.second", seconds) ?: "${seconds}s")
        }
    }
    return parts.joinToString(" ")
}
