package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FLY
import com.imyvm.iwg.util.translator.getOnlinePlayers
import com.imyvm.iwg.util.translator.getPlayerByUuid
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

private val pendingLanding: MutableMap<UUID, Long> = mutableMapOf()
private val systemGrantedFly: MutableSet<UUID> = mutableSetOf()
private val fallImmunity: MutableMap<UUID, Long> = mutableMapOf()

fun managePlayersFly(server: MinecraftServer) {
    val currentTick = server.overworld().gameTime
    for (player in getOnlinePlayers(server)) {
        processPlayerFly(player, currentTick)
    }
    processLandingCountdown(server, currentTick)
}

fun processPlayerFly(player: ServerPlayer) {
    processPlayerFly(player, player.level().server.overworld().gameTime)
}

private fun processPlayerFly(player: ServerPlayer, currentTick: Long) {
    val uuid = player.uuid
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level(), player.blockPosition().x, player.blockPosition().z)
    val canFlyNow = regionAndScope?.let { (region, scope) ->
        hasPermission(region, uuid, PermissionKey.FLY, scope, PERMISSION_DEFAULT_FLY.value)
    } ?: false

    if (canFlyNow) {
        if (!player.abilities.mayfly && uuid !in systemGrantedFly) {
            enableFlying(player)
        } else if (uuid in pendingLanding) {
            pendingLanding.remove(uuid)
            player.sendSystemMessage(Translator.tr("setting.permission.fly.restored")!!)
        }
    } else {
        if (uuid in systemGrantedFly && uuid !in pendingLanding) {
            pendingLanding[uuid] = tickDeadline(
                currentTick,
                PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value
            )
            player.sendSystemMessage(
                Translator.tr("setting.permission.fly.disabled.soon",
                PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value)!!)
        }
    }
}

private fun enableFlying(player: ServerPlayer) {
    if (!player.abilities.mayfly) {
        player.abilities.mayfly = true
        player.onUpdateAbilities()
        player.sendSystemMessage(Translator.tr("setting.permission.fly.enabled")!!)
        systemGrantedFly.add(player.uuid)
    }
    pendingLanding.remove(player.uuid)
}

private fun processLandingCountdown(server: MinecraftServer, currentTick: Long) {
    val iterator = pendingLanding.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        val uuid = entry.key
        val expiresAt = entry.value
        val player = getPlayerByUuid(server, uuid)
        if (player == null) { cleanupAbsentPlayer(uuid, iterator); continue }
        if (!isStillNoFly(player)) { iterator.remove(); continue }
        handleLandingEnd(player, uuid, expiresAt, currentTick, iterator)
    }
}

private fun handleLandingEnd(
    player: ServerPlayer,
    uuid: UUID,
    expiresAt: Long,
    currentTick: Long,
    iterator: MutableIterator<MutableMap.MutableEntry<UUID, Long>>
) {
    if (currentTick >= expiresAt) {
        if (uuid in systemGrantedFly) disableFlying(player, uuid, currentTick)
        iterator.remove()
    }
}

private fun disableFlying(player: ServerPlayer, uuid: UUID, currentTick: Long) {
    player.abilities.mayfly = false
    player.abilities.flying = false
    grantFallImmunity(uuid, currentTick)
    player.onUpdateAbilities()
    player.sendSystemMessage(Translator.tr("setting.permission.fly.disabled")!!)
    systemGrantedFly.remove(uuid)
}

fun processFallImmunity(player: ServerPlayer, currentTick: Long) {
    fallImmunity[player.uuid]?.let { expireTick ->
        if (currentTick <= expireTick) player.fallDistance = 0.0
        else fallImmunity.remove(player.uuid)
    }
}

private fun grantFallImmunity(uuid: UUID, currentTick: Long) {
    fallImmunity[uuid] = tickDeadline(currentTick, PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS.value)
}

private fun cleanupAbsentPlayer(
    uuid: UUID,
    iterator: MutableIterator<MutableMap.MutableEntry<UUID, Long>>
) {
    iterator.remove()
    systemGrantedFly.remove(uuid)
    fallImmunity.remove(uuid)
}

private fun isStillNoFly(player: ServerPlayer): Boolean {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level() ,player.blockPosition().x, player.blockPosition().z)
    return regionAndScope?.let { (region, scope) ->
        !hasPermission(region, player.uuid, PermissionKey.FLY, scope, PERMISSION_DEFAULT_FLY.value)
    } ?: true
}

@Deprecated("Use the Long tick overload", ReplaceWith("processFallImmunity(player, currentTick.toLong())"))
fun processFallImmunity(player: ServerPlayer, currentTick: Int) =
    processFallImmunity(player, currentTick.toLong())

internal fun tickDeadline(currentTick: Long, seconds: Int): Long {
    require(seconds >= 0) { "duration must not be negative" }
    return Math.addExact(currentTick, seconds.toLong() * 20L)
}
