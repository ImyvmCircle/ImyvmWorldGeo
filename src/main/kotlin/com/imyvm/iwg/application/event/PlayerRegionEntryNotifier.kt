package com.imyvm.iwg.application.event

import com.imyvm.iwg.application.region.PlayerRegionChecker
import com.imyvm.iwg.application.region.permission.helper.hasRegionPermission
import com.imyvm.iwg.application.region.permission.helper.hasScopePermission
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.PermissionKey
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

object PlayerRegionEntryExitTracker {

    private val playerStates = mutableMapOf<UUID, PlayerLocationState>()

    internal fun onRegionDeleted(region: Region) {
        playerStates.removeStatesReferencing(region)
    }

    internal fun onRegionMerged(source: Region, target: Region) {
        playerStates.retargetStates(source, target)
    }

    fun processTransitions(server: MinecraftServer) {
        val allCurrent = PlayerRegionChecker.getAllRegionScopesWithPlayers()
        val now = System.currentTimeMillis()

        for ((uuid, currentPair) in allCurrent) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            val current = PlayerLocation(currentPair.first, currentPair.second)
            val previous = playerStates[uuid]
            if (previous == null) {
                playerStates[uuid] = initialPlayerLocationState(current, now)
                continue
            }

            val transition = calculateLocationTransition(
                previous,
                current,
                now,
                ENTRY_EXIT_REGION_DELAY_SECONDS.value * 1000L
            )
            applyTransition(player, transition, now)
            playerStates[uuid] = transition.state
        }

        playerStates.keys.retainAll(allCurrent.keys)
        processPendingEntryTitles(server, now)
    }

    fun handleDisconnect(player: ServerPlayer) {
        val uuid = player.uuid
        val now = System.currentTimeMillis()
        val state = playerStates.remove(uuid) ?: return
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
            state.copy(stayStartedAt = null)
        }
    }

    private fun applyTransition(player: ServerPlayer, transition: LocationTransition, now: Long) {
        val uuid = player.uuid
        transition.regionExit?.let { sendRegionExitTitle(player, it) }
        transition.regionEntry?.let { sendRegionEntryTitle(player, it) }
        transition.scopeExit?.let { sendScopeExitMessage(player, it.region, it.scope) }
        transition.scopeEntry?.let { sendScopeEntryMessage(player, it.region, it.scope) }
        transition.completedStay?.let { addStayDuration(it.region, uuid, it.startedAt, it.endedAt) }
        transition.incrementEntry?.let { RegionDatabase.recordRegionEntry(it, uuid) }
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
            RegionDatabase.recordRegionStayDuration(region, uuid, delta)
        }
    }

    private fun sendRegionExitTitle(player: ServerPlayer, region: Region) {
        if (!isRegionNotificationEnabled(region)) return
        val text = getRegionMessage(region, EntryExitMessageKey.EXIT_MESSAGE)
            ?: translateConfigured(REGION_EXIT_I18N_KEY.value, region.name)
            ?: return
        player.connection.send(ClientboundSetTitlesAnimationPacket(5, 50, 15))
        player.connection.send(ClientboundSetTitleTextPacket(text))
    }

    private fun sendRegionEntryTitle(player: ServerPlayer, region: Region) {
        if (isRegionNotificationEnabled(region)) {
            val text = getRegionMessage(region, EntryExitMessageKey.ENTER_MESSAGE)
                ?: translateConfigured(REGION_ENTER_I18N_KEY.value, region.name)
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
            ?: translateConfigured(SCOPE_EXIT_I18N_KEY.value, region.name, scope.scopeName)
            ?: return
        player.sendSystemMessage(text)
    }

    private fun sendScopeEntryMessage(player: ServerPlayer, region: Region, scope: GeoScope) {
        if (isScopeNotificationEnabled(scope)) {
            val text = getScopeMessage(scope, EntryExitMessageKey.ENTER_MESSAGE, region.name, scope.scopeName)
                ?: translateConfigured(SCOPE_ENTER_I18N_KEY.value, region.name, scope.scopeName)
            if (text != null) player.sendSystemMessage(text)
        }
        sendRpgEntryNotifications(player, region, scope, scope.scopeName)
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
                val msg = Translator.tr(i18nKey, locationName)
                player.sendSystemMessage(msg)
            }
        }
    }

    private fun translateConfigured(key: String, vararg args: Any?): Component? {
        return if (Translator.hasTranslation(key)) Translator.tr(key, *args) else null
    }
}
