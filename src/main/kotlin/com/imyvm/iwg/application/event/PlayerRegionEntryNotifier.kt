package com.imyvm.iwg.application.event

import com.imyvm.iwg.application.region.PlayerRegionChecker
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.ENTRY_EXIT_REGION_DELAY_SECONDS
import com.imyvm.iwg.util.text.TextParser
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private const val DEFAULT_REGION_ENTER_TEMPLATE = "&6&lWelcome to &e{0}&6&l!"
private const val DEFAULT_REGION_EXIT_TEMPLATE = "&7You have left &e{0}&7."
private const val DEFAULT_SCOPE_ENTER_TEMPLATE = "&aWelcome to &e{0}&a territory, zone &b{1}&a."
private const val DEFAULT_SCOPE_EXIT_TEMPLATE = "&7You have left zone &b{1}&7 of &e{0}&7."

private data class PendingWildernessExit(val fromRegion: Region, val startedAt: Long)

private data class ScheduledEntryTitle(val playerUUID: UUID, val region: Region, val scheduledAt: Long)

object PlayerRegionEntryExitTracker {

    private val confirmedRegionMap: ConcurrentHashMap<UUID, Region> = ConcurrentHashMap()
    private val confirmedScopeMap: ConcurrentHashMap<UUID, GeoScope> = ConcurrentHashMap()
    private val confirmedSet: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val pendingWildernessExitMap: ConcurrentHashMap<UUID, PendingWildernessExit> = ConcurrentHashMap()
    private val pendingEntryTitles: ConcurrentLinkedQueue<ScheduledEntryTitle> = ConcurrentLinkedQueue()

    fun processTransitions(server: MinecraftServer) {
        val allCurrent = PlayerRegionChecker.getAllRegionScopesWithPlayers()
        val now = System.currentTimeMillis()

        for ((uuid, currentPair) in allCurrent) {
            val player = server.playerManager.getPlayer(uuid) ?: continue
            val currentRegion = currentPair.first
            val currentScope = currentPair.second

            if (!confirmedSet.contains(uuid)) {
                confirmedSet.add(uuid)
                if (currentRegion != null) confirmedRegionMap[uuid] = currentRegion
                if (currentScope != null) confirmedScopeMap[uuid] = currentScope
                continue
            }

            processRegionTransition(player, currentRegion, now)
            processScopeTransition(player, currentRegion, currentScope)
        }

        val onlineUUIDs = allCurrent.keys
        confirmedSet.retainAll(onlineUUIDs)
        confirmedRegionMap.keys.retainAll(onlineUUIDs)
        confirmedScopeMap.keys.retainAll(onlineUUIDs)
        pendingWildernessExitMap.keys.retainAll(onlineUUIDs)

        processPendingEntryTitles(server, now)
    }

    private fun processRegionTransition(
        player: ServerPlayerEntity,
        currentRegion: Region?,
        now: Long
    ) {
        val uuid = player.uuid
        val confirmed = confirmedRegionMap[uuid]
        val pending = pendingWildernessExitMap[uuid]
        val delayMs = ENTRY_EXIT_REGION_DELAY_SECONDS.value * 1000L

        if (currentRegion == confirmed) {
            pendingWildernessExitMap.remove(uuid)
            return
        }

        if (pending != null) {
            if (currentRegion == null) {
                if (now - pending.startedAt >= delayMs) {
                    sendRegionExitTitle(player, pending.fromRegion)
                    confirmedRegionMap.remove(uuid)
                    pendingWildernessExitMap.remove(uuid)
                }
            } else {
                sendRegionExitTitle(player, pending.fromRegion)
                schedulePendingEntryTitle(uuid, currentRegion, now)
                confirmedRegionMap[uuid] = currentRegion
                pendingWildernessExitMap.remove(uuid)
            }
        } else {
            if (currentRegion == null) {
                if (confirmed != null) {
                    pendingWildernessExitMap[uuid] = PendingWildernessExit(confirmed, now)
                }
            } else {
                if (confirmed != null) {
                    sendRegionExitTitle(player, confirmed)
                    schedulePendingEntryTitle(uuid, currentRegion, now)
                } else {
                    sendRegionEntryTitle(player, currentRegion)
                }
                confirmedRegionMap[uuid] = currentRegion
            }
        }
    }

    private fun processScopeTransition(
        player: ServerPlayerEntity,
        currentRegion: Region?,
        currentScope: GeoScope?
    ) {
        val uuid = player.uuid
        val confirmedScope = confirmedScopeMap[uuid]
        if (currentScope == confirmedScope) return

        if (confirmedScope != null) {
            sendScopeExitMessage(player, confirmedRegionMap[uuid], confirmedScope)
        }
        if (currentScope != null) {
            sendScopeEntryMessage(player, currentRegion, currentScope)
        }
        if (currentScope != null) confirmedScopeMap[uuid] = currentScope else confirmedScopeMap.remove(uuid)
    }

    private fun processPendingEntryTitles(server: MinecraftServer, now: Long) {
        val iterator = pendingEntryTitles.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (now - pending.scheduledAt >= 1000L) {
                iterator.remove()
                val player = server.playerManager.getPlayer(pending.playerUUID) ?: continue
                sendRegionEntryTitle(player, pending.region)
            }
        }
    }

    private fun schedulePendingEntryTitle(uuid: UUID, region: Region, now: Long) {
        pendingEntryTitles.add(ScheduledEntryTitle(uuid, region, now))
    }

    private fun sendRegionExitTitle(player: ServerPlayerEntity, region: Region) {
        if (!isRegionNotificationEnabled(region)) return
        val template = getOrAutoSetRegionMessage(region, EntryExitMessageKey.EXIT_MESSAGE, DEFAULT_REGION_EXIT_TEMPLATE)
        val message = template.replace("{0}", region.name)
        player.networkHandler.sendPacket(TitleFadeS2CPacket(5, 50, 15))
        player.networkHandler.sendPacket(TitleS2CPacket(TextParser.parse(message)))
    }

    private fun sendRegionEntryTitle(player: ServerPlayerEntity, region: Region) {
        if (!isRegionNotificationEnabled(region)) return
        val template = getOrAutoSetRegionMessage(region, EntryExitMessageKey.ENTER_MESSAGE, DEFAULT_REGION_ENTER_TEMPLATE)
        val message = template.replace("{0}", region.name)
        player.networkHandler.sendPacket(TitleFadeS2CPacket(5, 50, 15))
        player.networkHandler.sendPacket(TitleS2CPacket(TextParser.parse(message)))
    }

    private fun sendScopeExitMessage(player: ServerPlayerEntity, region: Region?, scope: GeoScope) {
        if (!isScopeNotificationEnabled(scope)) return
        val template = getOrAutoSetScopeMessage(scope, EntryExitMessageKey.EXIT_MESSAGE, DEFAULT_SCOPE_EXIT_TEMPLATE)
        val message = template.replace("{0}", region?.name ?: "").replace("{1}", scope.scopeName)
        player.sendMessage(TextParser.parse(message))
    }

    private fun sendScopeEntryMessage(player: ServerPlayerEntity, region: Region?, scope: GeoScope) {
        if (!isScopeNotificationEnabled(scope)) return
        val template = getOrAutoSetScopeMessage(scope, EntryExitMessageKey.ENTER_MESSAGE, DEFAULT_SCOPE_ENTER_TEMPLATE)
        val message = template.replace("{0}", region?.name ?: "").replace("{1}", scope.scopeName)
        player.sendMessage(TextParser.parse(message))
    }

    private fun isRegionNotificationEnabled(region: Region): Boolean {
        return region.settings
            .filterIsInstance<EntryExitToggleSetting>()
            .firstOrNull { it.key == EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED }
            ?.value ?: true
    }

    private fun isScopeNotificationEnabled(scope: GeoScope): Boolean {
        return scope.settings
            .filterIsInstance<EntryExitToggleSetting>()
            .firstOrNull { it.key == EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED }
            ?.value ?: true
    }

    private fun getOrAutoSetRegionMessage(region: Region, key: EntryExitMessageKey, defaultTemplate: String): String {
        val existing = region.settings
            .filterIsInstance<EntryExitMessageSetting>()
            .firstOrNull { it.key == key }
        if (existing != null) return existing.value
        region.settings.add(EntryExitMessageSetting(key, defaultTemplate))
        RegionDatabase.save()
        return defaultTemplate
    }

    private fun getOrAutoSetScopeMessage(scope: GeoScope, key: EntryExitMessageKey, defaultTemplate: String): String {
        val existing = scope.settings
            .filterIsInstance<EntryExitMessageSetting>()
            .firstOrNull { it.key == key }
        if (existing != null) return existing.value
        scope.settings.add(EntryExitMessageSetting(key, defaultTemplate))
        RegionDatabase.save()
        return defaultTemplate
    }
}
