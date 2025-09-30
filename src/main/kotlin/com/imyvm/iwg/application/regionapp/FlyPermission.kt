package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.util.ui.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.util.LazyTicker
import com.imyvm.iwg.util.setting.hasPermissionWhitelist
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

private val pendingLanding: MutableMap<UUID, Int> = mutableMapOf()
private val systemGrantedFly: MutableSet<UUID> = mutableSetOf()

fun registerFlyPermission() {
    LazyTicker.registerTask { server ->
        playerFlyManagement(server)
    }
}

private fun playerFlyManagement(server: MinecraftServer) {
    for (player in server.playerManager.playerList) {
        val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(player.blockX, player.blockZ)

        if (regionAndScope != null) {
            val (region, scope) = regionAndScope
            val canFly = hasPermissionWhitelist(region, player.uuid, PermissionKey.FLY, scope)

            if (canFly) {
                if (!player.abilities.allowFlying) {
                    player.abilities.allowFlying = true
                    player.sendAbilitiesUpdate()
                    player.sendMessage(Translator.tr("setting.permission.fly.enabled"))
                    systemGrantedFly.add(player.uuid)
                }
                pendingLanding.remove(player.uuid)
            } else {
                handleNoFlyZone(player)
            }
        } else {
            handleNoFlyZone(player)
        }
    }

    processLandingCountdown(server)
}

private fun handleNoFlyZone(player: ServerPlayerEntity) {
    if (player.uuid in systemGrantedFly && player.abilities.allowFlying) {
        if (!pendingLanding.containsKey(player.uuid)) {
            pendingLanding[player.uuid] = 100
            player.sendMessage(Translator.tr("setting.permission.fly.disabled.soon"))
        }
    }
}

private fun processLandingCountdown(server: MinecraftServer) {
    val iterator = pendingLanding.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        val uuid = entry.key
        val ticksLeft = entry.value - 20

        val player = server.playerManager.getPlayer(uuid)
        if (player == null) {
            iterator.remove()
            systemGrantedFly.remove(uuid)
            continue
        }

        val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(player.blockX, player.blockZ)
        val stillNoFly = regionAndScope?.let { (region, scope) ->
            !hasPermissionWhitelist(region, player.uuid, PermissionKey.FLY, scope)
        } ?: true

        if (!stillNoFly) {
            iterator.remove()
            continue
        }

        if (ticksLeft <= 0) {
            if (uuid in systemGrantedFly) {
                player.abilities.allowFlying = false
                player.abilities.flying = false
                player.sendAbilitiesUpdate()
                player.sendMessage(Translator.tr("setting.permission.fly.disabled"))
                systemGrantedFly.remove(uuid)
            }
            iterator.remove()
        } else {
            pendingLanding[uuid] = ticksLeft
        }
    }
}
