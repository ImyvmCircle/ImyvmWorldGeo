package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.regionapp.PlayerRegionChecker
import com.imyvm.iwg.infra.LazyTicker

fun registerPlayerGeographyPair() {
    LazyTicker.registerTask { server ->
        PlayerRegionChecker.updatePlayerRegions(server)
    }
}