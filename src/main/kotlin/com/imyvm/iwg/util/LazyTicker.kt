package com.imyvm.iwg.util

import com.imyvm.iwg.ModConfig.Companion.LAZY_TICKER_SECONDS
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer

object LazyTicker {
    private var tickCounter: Int = 0
    private val tasks: MutableList<(MinecraftServer) -> Unit> = mutableListOf()

    fun registerLazyTicker(){
        ServerTickEvents.END_SERVER_TICK.register { server ->
            onTick(server)
        }
    }

    fun registerTask(task: (MinecraftServer) -> Unit) {
        tasks.add(task)
    }

    private fun onTick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter % (20 * LAZY_TICKER_SECONDS.value) != 0) return

        tasks.forEach { it(server) }
    }
}