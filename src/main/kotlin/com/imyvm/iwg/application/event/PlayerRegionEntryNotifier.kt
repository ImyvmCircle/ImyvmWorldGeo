package com.imyvm.iwg.application.event

import com.imyvm.iwg.application.region.PlayerRegionChecker
import com.imyvm.iwg.application.region.permission.helper.hasRegionPermission
import com.imyvm.iwg.application.region.permission.helper.hasScopePermission
import com.imyvm.iwg.application.space.WorldGeoSpaceSupport
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoSpaceLevel
import com.imyvm.iwg.domain.WorldGeoSubSpaceTransition
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.BehaviorStatsStore
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.EntryExitConfig.ENTRY_EXIT_REGION_DELAY_SECONDS
import com.imyvm.iwg.infra.config.EntryExitConfig.REGION_ENTER_I18N_KEY
import com.imyvm.iwg.infra.config.EntryExitConfig.REGION_EXIT_I18N_KEY
import com.imyvm.iwg.infra.config.EntryExitConfig.SCOPE_ENTER_I18N_KEY
import com.imyvm.iwg.infra.config.EntryExitConfig.SCOPE_EXIT_I18N_KEY
import com.imyvm.iwg.infra.config.PermissionConfig
import com.imyvm.iwg.util.text.TextParser
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.UUID

private const val AFK_THRESHOLD_MILLIS = 300_000L

object PlayerRegionEntryExitTracker {

    private val playerStates = mutableMapOf<UUID, PlayerLocationState>()

    fun processTransitions(server: MinecraftServer) {
        val allCurrent = PlayerRegionChecker.getAllRegionScopesWithPlayers()
        val now = System.currentTimeMillis()

        for ((uuid, currentPair) in allCurrent) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            val current = PlayerLocation(currentPair.first, currentPair.second, currentPair.third)
            val previous = playerStates[uuid]
            if (previous == null) {
                playerStates[uuid] = initialPlayerLocationState(current, now)
                continue
            }

            recordSampledDurations(player, previous.location, previous.sampledAtMillis, now)
            val transition = calculateLocationTransition(
                previous,
                current,
                now,
                ENTRY_EXIT_REGION_DELAY_SECONDS.value * 1000L
            )
            applyTransition(player, transition, now)
            playerStates[uuid] = transition.state.copy(sampledAtMillis = now)
        }

        playerStates.keys.retainAll(allCurrent.keys)
        processPendingEntryTitles(server, now)
    }

    fun handleDisconnect(player: ServerPlayer) {
        val uuid = player.uuid
        val now = System.currentTimeMillis()
        val state = playerStates.remove(uuid) ?: return
        recordSampledDurations(player, state.location, state.sampledAtMillis, now)
        val region = state.location.region
        val startedAt = state.stayStartedAt
        if (region != null && startedAt != null) {
            addStayDuration(region, uuid, startedAt, now)
        }
    }

    fun flushAllDurations() {
        val now = System.currentTimeMillis()
        playerStates.replaceAll { uuid, state ->
            val region = state.location.region
            val startedAt = state.stayStartedAt
            if (region != null && startedAt != null) {
                addStayDuration(region, uuid, startedAt, now)
            }
            state.copy(stayStartedAt = null, sampledAtMillis = now)
        }
    }

    private fun applyTransition(player: ServerPlayer, transition: LocationTransition, now: Long) {
        val uuid = player.uuid
        transition.regionExit?.let {
            sendRegionExitTitle(player, it)
            recordRegionSpaceEvent(WorldGeoBehaviorType.SPACE_EXIT, player, it, now)
        }
        transition.regionEntry?.let {
            sendRegionEntryTitle(player, it)
            recordRegionSpaceEvent(WorldGeoBehaviorType.SPACE_ENTER, player, it, now)
        }
        transition.scopeExit?.let {
            sendScopeExitMessage(player, it.region, it.scope)
            recordScopeSpaceEvent(WorldGeoBehaviorType.SPACE_EXIT, player, it, now)
        }
        transition.scopeEntry?.let {
            sendScopeEntryMessage(player, it.region, it.scope)
            recordScopeSpaceEvent(WorldGeoBehaviorType.SPACE_ENTER, player, it, now)
        }
        transition.subSpaceExit?.let { location ->
            location.subSpace?.let {
                sendSubSpaceExitMessage(player, location.region, location.scope, it)
                recordSubSpaceEvent(WorldGeoBehaviorType.SPACE_EXIT, player, location, now)
            }
        }
        transition.subSpaceEntry?.let { location ->
            location.subSpace?.let {
                sendSubSpaceEntryMessage(player, location.region, location.scope, it)
                recordSubSpaceEvent(WorldGeoBehaviorType.SPACE_ENTER, player, location, now)
            }
        }
        transition.completedStay?.let { addStayDuration(it.region, uuid, it.startedAt, it.endedAt) }
        transition.incrementEntry?.let { RegionDatabase.incrementRegionEntryStat(it, uuid) }
        transition.regionEvent?.let { (from, to) ->
            RegionTransitionEvent.EVENT.invoker().onTransition(player, from, to, now)
        }
        transition.scopeEvent?.let { (from, to) ->
            ScopeTransitionEvent.EVENT.invoker().onTransition(
                player,
                from?.let { it.region to it.scope },
                to?.let { it.region to it.scope },
                now
            )
        }
        buildSubSpaceTransitionPayload(player.uuid, player.scoreboardName, transition.subSpaceExit, transition.subSpaceEntry, now)
            ?.let(SubSpaceTransitionEvent::publish)
    }

    private fun recordRegionSpaceEvent(type: WorldGeoBehaviorType, player: ServerPlayer, region: Region, now: Long) {
        recordSpaceBehavior(
            type,
            player,
            region.name,
            region.numberID,
            null,
            null,
            null,
            null,
            WorldGeoSpaceLevel.REGION,
            now
        )
    }

    private fun recordScopeSpaceEvent(type: WorldGeoBehaviorType, player: ServerPlayer, location: ScopedPlayerLocation, now: Long) {
        recordSpaceBehavior(
            type,
            player,
            location.region.name,
            location.region.numberID,
            location.scope.scopeName,
            location.scope.assignedScopeIdOrNull?.raw,
            null,
            null,
            WorldGeoSpaceLevel.SCOPE,
            now
        )
    }

    private fun recordSubSpaceEvent(type: WorldGeoBehaviorType, player: ServerPlayer, location: ScopedPlayerLocation, now: Long) {
        val subSpace = location.subSpace ?: return
        recordSpaceBehavior(
            type,
            player,
            location.region.name,
            location.region.numberID,
            location.scope.scopeName,
            location.scope.assignedScopeIdOrNull?.raw,
            subSpace.name,
            subSpace.subSpaceId,
            WorldGeoSpaceLevel.SUBSPACE,
            now
        )
    }

    private fun processPendingEntryTitles(server: MinecraftServer, now: Long) {
        playerStates.replaceAll { uuid, state ->
            val pending = state.scheduledEntryTitle
            if (pending != null && now - pending.scheduledAt >= 1000L) {
                server.playerList.getPlayer(uuid)?.let { sendRegionEntryTitle(it, pending.region) }
                state.copy(scheduledEntryTitle = null)
            } else {
                state
            }
        }
    }

    private fun addStayDuration(region: Region, uuid: UUID, startedAt: Long, endedAt: Long) {
        val delta = endedAt - startedAt
        if (delta > 0L) {
            RegionDatabase.addRegionStayDuration(region, uuid, delta)
        }
    }

    private fun recordSampledDurations(player: ServerPlayer, location: PlayerLocation, startedAt: Long, endedAt: Long) {
        if (location.region == null) return
        val millis = endedAt - startedAt
        if (millis <= 0L) return
        val event = sampledDurationEvent(player, location, endedAt)
        val chunk = player.blockPosition()
        BehaviorStatsStore.recordOnlineMillis(event, millis)
        val afkMillis = afkDurationMillis(player, startedAt, endedAt)
        if (afkMillis > 0L) BehaviorStatsStore.recordOnlineMillis(event, afkMillis, afk = true)
        BehaviorStatsStore.recordResidenceMillis(event, chunk.x shr 4, chunk.z shr 4, millis)
    }

    private fun afkDurationMillis(player: ServerPlayer, startedAt: Long, endedAt: Long): Long {
        val idleStart = Math.addExact(player.lastActionTime, AFK_THRESHOLD_MILLIS)
        return (endedAt - maxOf(startedAt, idleStart)).coerceAtLeast(0L)
    }

    private fun sampledDurationEvent(player: ServerPlayer, location: PlayerLocation, unixMillis: Long): WorldGeoBehaviorEvent {
        val pos = player.blockPosition()
        return WorldGeoBehaviorEvent(
            type = WorldGeoBehaviorType.ITEM_USE,
            playerUuid = player.uuid,
            playerName = player.scoreboardName,
            dimensionId = player.level().dimension().identifier(),
            x = pos.x,
            y = pos.y,
            z = pos.z,
            unixMillis = unixMillis,
            regionId = location.region?.numberID,
            regionName = location.region?.name,
            scopeId = location.scope?.assignedScopeIdOrNull?.raw,
            scopeName = location.scope?.scopeName,
            subSpaceId = location.subSpace?.subSpaceId,
            subSpaceName = location.subSpace?.name,
            spaceLevel = when {
                location.subSpace != null -> WorldGeoSpaceLevel.SUBSPACE
                location.scope != null -> WorldGeoSpaceLevel.SCOPE
                else -> WorldGeoSpaceLevel.REGION
            }
        )
    }

    private fun sendRegionExitTitle(player: ServerPlayer, region: Region) {
        if (!isRegionNotificationEnabled(region)) return
        val text = getRegionMessage(region, EntryExitMessageKey.EXIT_MESSAGE)
            ?: Translator.tr(REGION_EXIT_I18N_KEY.value, region.name)
            ?: return
        player.connection.send(ClientboundSetTitlesAnimationPacket(5, 50, 15))
        player.connection.send(ClientboundSetTitleTextPacket(text))
    }

    private fun sendRegionEntryTitle(player: ServerPlayer, region: Region) {
        if (isRegionNotificationEnabled(region)) {
            val text = getRegionMessage(region, EntryExitMessageKey.ENTER_MESSAGE)
                ?: Translator.tr(REGION_ENTER_I18N_KEY.value, region.name)
            if (text != null) {
                player.connection.send(ClientboundSetTitlesAnimationPacket(5, 50, 15))
                player.connection.send(ClientboundSetTitleTextPacket(text))
            }
        }
        sendRpgEntryNotifications(player, region, null, region.name)
    }

    private fun sendScopeExitMessage(player: ServerPlayer, region: Region, scope: GeoScope) {
        if (!isScopeNotificationEnabled(scope)) return
        val text = getScopeMessage(scope, EntryExitMessageKey.EXIT_MESSAGE, region.name, scope.scopeName)
            ?: Translator.tr(SCOPE_EXIT_I18N_KEY.value, region.name, scope.scopeName)
            ?: return
        player.sendSystemMessage(text)
    }

    private fun sendScopeEntryMessage(player: ServerPlayer, region: Region, scope: GeoScope) {
        if (isScopeNotificationEnabled(scope)) {
            val text = getScopeMessage(scope, EntryExitMessageKey.ENTER_MESSAGE, region.name, scope.scopeName)
                ?: Translator.tr(SCOPE_ENTER_I18N_KEY.value, region.name, scope.scopeName)
            if (text != null) player.sendSystemMessage(text)
        }
        sendRpgEntryNotifications(player, region, scope, scope.scopeName)
    }


    private fun sendSubSpaceExitMessage(player: ServerPlayer, region: Region, scope: GeoScope, subSpace: SubSpace) {
        val text = Translator.tr("notification.subspace.exit", region.name, scope.scopeName, subSpace.name) ?: return
        player.sendSystemMessage(text)
    }

    private fun sendSubSpaceEntryMessage(player: ServerPlayer, region: Region, scope: GeoScope, subSpace: SubSpace) {
        val text = getSubSpaceEntryMessage(subSpace, region.name, scope.scopeName)
            ?: Translator.tr("notification.subspace.enter", region.name, scope.scopeName, subSpace.name)
            ?: return
        player.sendSystemMessage(text)
    }

    private fun isRegionNotificationEnabled(region: Region): Boolean {
        return region.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED) ?: true
    }

    private fun isScopeNotificationEnabled(scope: GeoScope): Boolean {
        return scope.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED) ?: true
    }

    private fun getRegionMessage(region: Region, key: EntryExitMessageKey): Component? {
        val raw = region.settingStore.entryExitMessage(key) ?: return null
        return TextParser.parse(raw)
    }

    private fun getScopeMessage(scope: GeoScope, key: EntryExitMessageKey, regionName: String, scopeName: String): Component? {
        val raw = scope.settingStore.entryExitMessage(key) ?: return null
        return TextParser.parse(raw.replace("{0}", regionName).replace("{1}", scopeName))
    }


    private fun getSubSpaceEntryMessage(subSpace: SubSpace, regionName: String, scopeName: String): Component? {
        val raw = subSpace.entryMessage ?: return null
        return TextParser.parse(raw.replace("{0}", regionName).replace("{1}", scopeName).replace("{2}", subSpace.name))
    }

    private fun sendRpgEntryNotifications(player: ServerPlayer, region: Region, scope: GeoScope?, locationName: String) {
        val rpgKeys = listOf(
            PermissionKey.RPG_ITEM_PICKUP to PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP,
            PermissionKey.RPG_BOW_SHOOT to PermissionConfig.PERMISSION_DEFAULT_RPG_BOW_SHOOT,
            PermissionKey.RPG_VEHICLE_USE to PermissionConfig.PERMISSION_DEFAULT_RPG_VEHICLE_USE,
            PermissionKey.RPG_EATING to PermissionConfig.PERMISSION_DEFAULT_RPG_EATING,
            PermissionKey.RPG_FISHING to PermissionConfig.PERMISSION_DEFAULT_RPG_FISHING
        )
        for ((key, configDefault) in rpgKeys) {
            val default = configDefault.getValue()
            val effective = if (scope == null) {
                hasRegionPermission(region, player.uuid, key, default)
            } else {
                hasScopePermission(region, scope, player.uuid, key, default)
            }
            if (!effective) {
                val i18nKey = "notification.rpg.${key.name.lowercase().removePrefix("rpg_")}_restricted"
                val msg = Translator.tr(i18nKey, locationName) ?: continue
                player.sendSystemMessage(msg)
            }
        }
    }
}

internal fun buildSubSpaceTransitionPayload(
    playerUuid: UUID,
    playerName: String,
    from: ScopedPlayerLocation?,
    to: ScopedPlayerLocation?,
    gameTimeMillis: Long
): WorldGeoSubSpaceTransition? {
    val fromSnapshot = from?.subSpace?.let { WorldGeoSpaceSupport.snapshot(from.region, from.scope, it) }
    val toSnapshot = to?.subSpace?.let { WorldGeoSpaceSupport.snapshot(to.region, to.scope, it) }
    if (fromSnapshot == null && toSnapshot == null) return null
    return WorldGeoSubSpaceTransition(playerUuid, playerName, fromSnapshot, toSnapshot, gameTimeMillis)
}
