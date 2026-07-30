package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.time.WorldGeoPeriodTimelineService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorCaptureState
import com.imyvm.iwg.infra.BehaviorStatsPageStreamService
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.SegmentedBehaviorStatsStore
import com.imyvm.iwg.infra.config.CoreConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.server.level.ServerPlayer

internal data class BehaviorStorageDebugSnapshot(
    val timeline: String,
    val health: String,
    val captureState: WorldGeoBehaviorCaptureState,
    val pendingEntries: Int,
    val pendingBytes: Long,
    val thresholds: String,
    val droppedInterval: String,
    val ioQueue: Int,
    val ioActive: Int,
    val manifestVersion: Long,
    val segmentCount: Int,
    val cache: String,
    val earliestPeriods: String
)

internal fun behaviorStorageDebugSnapshot(): BehaviorStorageDebugSnapshot {
    val timelines = runCatching { WorldGeoPeriodTimelineService.availableTimelines() }
        .getOrDefault(emptyList())
    val current = runCatching { WorldGeoPeriodTimelineService.currentPeriodKeys() }
        .getOrDefault(emptyMap())
    val earliest = timelines.flatMap { timeline ->
        NaturalPeriodKind.entries.mapNotNull { kind ->
            WorldGeoPeriodTimelineService.availablePeriodRange(timeline.timelineId, kind)
                ?.let { "${timeline.timelineId}:${kind.name}=${it.earliest.periodId}" }
        }
    }.joinToString(",").ifEmpty { "none" }
    val pendingEntries = BehaviorStatsStore.pendingEntryCount()
    val pendingBytes = BehaviorStatsStore.estimatedPendingBytes()
    val health = when {
        BehaviorStatsStore.captureState() == WorldGeoBehaviorCaptureState.CAPTURE_SUSPENDED -> "SUSPENDED"
        BehaviorStatsStore.warningActive() -> "WARNING"
        else -> "HEALTHY"
    }
    val interval = BehaviorStatsStore.activeMissingInterval()
    return BehaviorStorageDebugSnapshot(
        timeline = "available=${timelines.size} current=" +
            current.entries.sortedBy { it.key.ordinal }
                .joinToString(",") { "${it.key.name}=${it.value.timelineId}:${it.value.periodId}" },
        health = health,
        captureState = BehaviorStatsStore.captureState(),
        pendingEntries = pendingEntries,
        pendingBytes = pendingBytes,
        thresholds = "entries=${CoreConfig.BEHAVIOR_STATS_WARNING_ENTRY_COUNT.value}/" +
            "${CoreConfig.BEHAVIOR_STATS_MAX_ENTRY_COUNT.value} bytes=" +
            "${CoreConfig.BEHAVIOR_STATS_WARNING_ESTIMATED_BYTES.value}/" +
            "${CoreConfig.BEHAVIOR_STATS_MAX_ESTIMATED_BYTES.value} recovery=75%",
        droppedInterval = interval?.let {
            "${it.startMillis}..${it.endMillis ?: "open"} dropped=${it.droppedEventCount}"
        } ?: "none",
        ioQueue = SegmentedBehaviorStatsStore.queuedIoOperationCount(),
        ioActive = SegmentedBehaviorStatsStore.activeIoOperationCount(),
        manifestVersion = SegmentedBehaviorStatsStore.manifestVersion(),
        segmentCount = SegmentedBehaviorStatsStore.segmentCount(),
        cache = "pages=${BehaviorStatsPageStreamService.cachedPageCount()}/" +
            "${BehaviorStatsPageStreamService.MAX_CACHED_PAGE_COUNT} handles=" +
            "${BehaviorStatsPageStreamService.activeHandleCount()}/" +
            "${BehaviorStatsPageStreamService.MAX_ACTIVE_HANDLES} pageSize=" +
            "${BehaviorStatsPageStreamService.DEFAULT_PAGE_SIZE}/" +
            "${BehaviorStatsPageStreamService.MAX_PAGE_SIZE} evictions=" +
            BehaviorStatsPageStreamService.cacheEvictionCount(),
        earliestPeriods = earliest
    )
}

fun onDebugBehaviorStorage(player: ServerPlayer): Int {
    val snapshot = behaviorStorageDebugSnapshot()
    val refreshCommand = "/imyvmWorldGeo debug behavior storage"
    val refresh = Translator.tr("interaction.meta.debug.behavior.storage.refresh")!!.copy()
        .setStyle(
            Style.EMPTY
                .withColor(TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
                .withClickEvent(ClickEvent.RunCommand(refreshCommand))
                .withHoverEvent(
                    HoverEvent.ShowText(
                        Translator.tr(
                            "interaction.meta.debug.behavior.storage.refresh.hover",
                            refreshCommand
                        )!!
                    )
                )
        )
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.storage.header",
            player.scoreboardName
        )!!.copy().append(Component.literal(" ")).append(refresh)
    )
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.storage.timeline",
            snapshot.timeline,
            snapshot.earliestPeriods
        )!!
    )
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.storage.health",
            snapshot.health,
            snapshot.captureState,
            snapshot.pendingEntries,
            snapshot.pendingBytes,
            snapshot.thresholds
        )!!
    )
    player.sendSystemMessage(
        Translator.tr(
            "interaction.meta.debug.behavior.storage.io",
            snapshot.droppedInterval,
            snapshot.ioQueue,
            snapshot.ioActive,
            snapshot.manifestVersion,
            snapshot.segmentCount,
            snapshot.cache
        )!!
    )
    return 1
}
