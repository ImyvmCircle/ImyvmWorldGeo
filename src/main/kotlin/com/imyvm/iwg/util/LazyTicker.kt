package com.imyvm.iwg.util

import net.minecraft.server.MinecraftServer

object LazyTicker {
    private var tickCounter: Int = 0
    private val tasks: MutableList<(MinecraftServer) -> Unit> = mutableListOf()

    fun registerTask(task: (MinecraftServer) -> Unit) {
        tasks.add(task)
    }

    fun onTick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter % 20 != 0) return

        tasks.forEach { it(server) }
    }
}