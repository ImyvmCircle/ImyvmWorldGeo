package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.selection.display.displaySelectionForAllPlayers
import com.imyvm.iwg.application.selection.display.displayScopeBoundariesForActionBarPlayers
import com.imyvm.iwg.infra.LazyTicker

fun registerSelectionDisplay() {
    LazyTicker.registerTask { server ->
        displaySelectionForAllPlayers(server)
        displayScopeBoundariesForActionBarPlayers(server)
    }
}
