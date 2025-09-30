package com.imyvm.iwg.inter.register

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.util.LazyTicker

fun registerPlayerGeographyPair() {
    LazyTicker.registerTask { server ->
        ImyvmWorldGeo.playerRegionChecker.updatePlayerRegions(server)
    }
}