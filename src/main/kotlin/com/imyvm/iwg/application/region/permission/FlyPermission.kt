package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.application.region.permission.helper.hasPermissionWhitelist
import com.imyvm.iwg.util.translator.getOnlinePlayers
import com.imyvm.iwg.util.translator.getPlayerByUuid
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

private val pendingLanding: MutableMap<UUID, Int> = mutableMapOf()
private val systemGrantedFly: MutableSet<UUID> = mutableSetOf()
private val fallImmunity: MutableMap<UUID, Int> = mutableMapOf()

fun managePlayersFly(server: MinecraftServer) {
    val currentTick = server.overworld.time.toInt()
    for (player in getOnlinePlayers(server)) {
        processPlayerFly(player)
    }
    processLandingCountdown(server, currentTick)
}

fun processPlayerFly(player: ServerPlayerEntity) {
    val uuid = player.uuid
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, player.blockX, player.blockZ)
    val canFlyNow = regionAndScope?.let { (region, scope) ->
        hasPermissionWhitelist(region, uuid, PermissionKey.FLY, scope)
    } ?: false

    if (canFlyNow) {
        if (!player.abilities.allowFlying && uuid !in systemGrantedFly) {
            enableFlying(player)
        } else if (uuid in pendingLanding) {
            pendingLanding.remove(uuid)
            player.sendMessage(Translator.tr("setting.permission.fly.restored"))
        }
    } else {
        if (uuid in systemGrantedFly && uuid !in pendingLanding) {
            pendingLanding[uuid] = PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value * 20
            player.sendMessage(
                Translator.tr("setting.permission.fly.disabled.soon",
                PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value))
        }
    }
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

private fun processLandingCountdown(server: MinecraftServer, currentTick: Int) {
    val iterator = pendingLanding.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        val uuid = entry.key
        val ticksLeft = entry.value - 20
        val player = getPlayerByUuid(server, uuid)
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

fun processFallImmunity(player: ServerPlayerEntity, currentTick: Int) {
    fallImmunity[player.uuid]?.let { expireTick ->
        if (currentTick <= expireTick) player.fallDistance = 0f
        else fallImmunity.remove(player.uuid)
    }
}

private fun grantFallImmunity(uuid: UUID, currentTick: Int) {
    val durationTicks = PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS.value * 20
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
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world ,player.blockX, player.blockZ)
    return regionAndScope?.let { (region, scope) ->
        !hasPermissionWhitelist(region, player.uuid, PermissionKey.FLY, scope)
    } ?: true
}
