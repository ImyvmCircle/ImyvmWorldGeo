package com.imyvm.iwg.application.event

import com.imyvm.iwg.infra.BehaviorStatsStore
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.function.Predicate

object BehaviorStorageAlertService {
    private const val ALERT_INTERVAL_MILLIS = 60_000L
    private var lastOnlineAlertAt = Long.MIN_VALUE

    fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            notifyJoiningOperator(handler.player)
        }
    }

    fun notifyOnlineOperators(server: MinecraftServer, nowMillis: Long = System.currentTimeMillis()) {
        val alert = BehaviorStatsStore.storageAlert() ?: return
        if (!alertDue(nowMillis, lastOnlineAlertAt, ALERT_INTERVAL_MILLIS)) return
        lastOnlineAlertAt = nowMillis
        server.playerList.players
            .filter(::isOperator)
            .forEach { it.sendSystemMessage(Component.literal(alert)) }
    }

    internal fun notifyJoiningOperator(player: ServerPlayer) {
        val alert = BehaviorStatsStore.storageAlert() ?: return
        if (isOperator(player)) player.sendSystemMessage(Component.literal(alert))
    }

    internal fun resetForSession() {
        lastOnlineAlertAt = Long.MIN_VALUE
    }

    private fun isOperator(player: ServerPlayer): Boolean {
        val predicate: Predicate<CommandSourceStack> = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
        return predicate.test(player.createCommandSourceStack())
    }
}

internal fun alertDue(nowMillis: Long, lastAlertAt: Long, intervalMillis: Long): Boolean =
    lastAlertAt == Long.MIN_VALUE || nowMillis < lastAlertAt || nowMillis - lastAlertAt >= intervalMillis
