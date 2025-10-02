package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.LazyTicker

fun registerPlayerGeographyPair() {
    LazyTicker.registerTask { server ->
        ImyvmWorldGeo.playerRegionChecker.updatePlayerRegions(server)
    }
}