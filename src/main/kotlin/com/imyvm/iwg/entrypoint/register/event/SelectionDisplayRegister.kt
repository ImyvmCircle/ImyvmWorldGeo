package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.selection.display.displaySelectionAndScopeBoundariesForAllPlayers
import com.imyvm.iwg.infra.LazyTicker

fun registerSelectionDisplay() {
    LazyTicker.registerTask { server ->
        displaySelectionAndScopeBoundariesForAllPlayers(server)
    }
}
