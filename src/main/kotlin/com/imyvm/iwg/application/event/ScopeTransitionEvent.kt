package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

object ScopeTransitionEvent {

    fun interface Callback {
        fun onTransition(
            player: ServerPlayer,
            from: Pair<Region, GeoScope>?,
            to: Pair<Region, GeoScope>?,
            gameTimeMillis: Long
        )
    }

    val EVENT: Event<Callback> = EventFactory.createArrayBacked(Callback::class.java) { callbacks ->
        Callback { player, from, to, gameTimeMillis ->
            for (callback in callbacks) {
                try {
                    callback.onTransition(player, from, to, gameTimeMillis)
                } catch (t: Throwable) {
                    com.imyvm.iwg.ImyvmWorldGeo.logger.warn(
                        "ScopeTransitionEvent subscriber threw: ${t.message}", t
                    )
                }
            }
        }
    }
}
