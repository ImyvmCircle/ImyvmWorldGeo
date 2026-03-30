package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.region.effect.applyRegionEffectsToPlayers
import com.imyvm.iwg.infra.LazyTicker

fun registerRegionEffects() {
    LazyTicker.registerTask { server ->
        applyRegionEffectsToPlayers(server)
    }
}