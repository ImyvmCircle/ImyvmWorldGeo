package com.imyvm.iwg.infra

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.config.CoreConfig.LAZY_TICKER_SECONDS
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer

object LazyTicker {
    private var tickCounter: Long = 0
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
        if (tickCounter < lazyIntervalTicks(LAZY_TICKER_SECONDS.value)) return
        tickCounter = 0

        runIsolated(tasks, server) {
            ImyvmWorldGeo.logger.error("Lazy ticker task failed: ${it.message}", it)
        }
    }
}

internal fun lazyIntervalTicks(seconds: Int): Long {
    require(seconds > 0) { "lazy ticker interval must be greater than 0" }
    return seconds.toLong() * 20L
}

internal fun <T> runIsolated(tasks: Iterable<(T) -> Unit>, value: T, onFailure: (Throwable) -> Unit) {
    tasks.forEach { task -> runCatching { task(value) }.onFailure(onFailure) }
}
