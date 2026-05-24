package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

object RegionTransitionEvent {

    fun interface Callback {
        fun onTransition(
            player: ServerPlayer,
            fromRegion: Region?,
            toRegion: Region?,
            gameTimeMillis: Long
        )
    }

    val EVENT: Event<Callback> = EventFactory.createArrayBacked(Callback::class.java) { callbacks ->
        Callback { player, fromRegion, toRegion, gameTimeMillis ->
            for (callback in callbacks) {
                try {
                    callback.onTransition(player, fromRegion, toRegion, gameTimeMillis)
                } catch (t: Throwable) {
                    com.imyvm.iwg.ImyvmWorldGeo.logger.warn(
                        "RegionTransitionEvent subscriber threw: ${t.message}", t
                    )
                }
            }
        }
    }
}
