package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.util.ui.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.util.LazyTicker
import com.imyvm.iwg.util.setting.hasPermissionWhitelist
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import com.imyvm.iwg.domain.Region
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

private val pendingLanding: MutableMap<UUID, Int> = mutableMapOf()
private val systemGrantedFly: MutableSet<UUID> = mutableSetOf()
private val fallImmunity: MutableMap<UUID, Int> = mutableMapOf()

fun registerFlyPermission() {
    LazyTicker.registerTask { server ->
        managePlayersFly(server)
    }

    ServerTickEvents.END_SERVER_TICK.register{ server ->
        for (player in server.playerManager.playerList) {
            val currentTick = server.overworld.time.toInt()
            processFallImmunity(player, currentTick)
        }
    }
}
private fun managePlayersFly(server: MinecraftServer) {
    val currentTick = server.overworld.time.toInt()
    for (player in server.playerManager.playerList) {
        processPlayerFly(player)
    }
    processLandingCountdown(server, currentTick)
}

private fun processPlayerFly(player: ServerPlayerEntity) {
    val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(player.blockX, player.blockZ)
    if (regionAndScope != null) handleFlyPermission(player, regionAndScope)
    else handleNoFlyZone(player)
}

private fun handleFlyPermission(
    player: ServerPlayerEntity,
    regionAndScope: Pair<Region, Region.Companion.GeoScope>
) {
    val (region, scope) = regionAndScope
    val canFly = hasPermissionWhitelist(region, player.uuid, PermissionKey.FLY, scope)
    if (canFly) enableFlying(player) else handleNoFlyZone(player)
}

private fun enableFlying(player: ServerPlayerEntity) {
    if (!player.abilities.allowFlying) {
        player.abilities.allowFlying = true
        player.sendAbilitiesUpdate()
        player.sendMessage(Translator.tr("setting.permission.fly.enabled"))
        systemGrantedFly.add(player.uuid)
    }
    pendingLanding.remove(player.uuid)
}

private fun handleNoFlyZone(player: ServerPlayerEntity) {
    if (player.uuid !in systemGrantedFly || !player.abilities.allowFlying) return
    if (player.uuid !in pendingLanding) {
        pendingLanding[player.uuid] = 100
        player.sendMessage(Translator.tr("setting.permission.fly.disabled.soon"))
    }
}

private fun processLandingCountdown(server: MinecraftServer, currentTick: Int) {
    val iterator = pendingLanding.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        val uuid = entry.key
        val ticksLeft = entry.value - 20
        val player = server.playerManager.getPlayer(uuid)
        if (player == null) { cleanupAbsentPlayer(uuid, iterator); continue }
        if (!isStillNoFly(player)) { iterator.remove(); continue }
        handleLandingEnd(player, uuid, ticksLeft, currentTick, iterator)
    }
}

private fun handleLandingEnd(
    player: ServerPlayerEntity,
    uuid: UUID,
    ticksLeft: Int,
    currentTick: Int,
    iterator: MutableIterator<MutableMap.MutableEntry<UUID, Int>>
) {
    if (ticksLeft <= 0) {
        if (uuid in systemGrantedFly) disableFlying(player, uuid, currentTick)
        iterator.remove()
    } else {
        pendingLanding[uuid] = ticksLeft
    }
}

private fun disableFlying(player: ServerPlayerEntity, uuid: UUID, currentTick: Int) {
    player.abilities.allowFlying = false
    player.abilities.flying = false
    grantFallImmunity(uuid, currentTick)
    player.sendAbilitiesUpdate()
    player.sendMessage(Translator.tr("setting.permission.fly.disabled"))
    systemGrantedFly.remove(uuid)
}

private fun processFallImmunity(player: ServerPlayerEntity, currentTick: Int) {
    fallImmunity[player.uuid]?.let { expireTick ->
        if (currentTick <= expireTick) player.fallDistance = 0f
        else fallImmunity.remove(player.uuid)
    }
}

private fun grantFallImmunity(uuid: UUID, currentTick: Int, durationTicks: Int = 20 * 10) {
    fallImmunity[uuid] = currentTick + durationTicks
}

private fun cleanupAbsentPlayer(
    uuid: UUID,
    iterator: MutableIterator<MutableMap.MutableEntry<UUID, Int>>
) {
    iterator.remove()
    systemGrantedFly.remove(uuid)
    fallImmunity.remove(uuid)
}

private fun isStillNoFly(player: ServerPlayerEntity): Boolean {
    val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(player.blockX, player.blockZ)
    return regionAndScope?.let { (region, scope) ->
        !hasPermissionWhitelist(region, player.uuid, PermissionKey.FLY, scope)
    } ?: true
}
