package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.PlayerRegionEntryExitTracker
import com.imyvm.iwg.infra.LazyTicker

fun registerRegionEntryExit() {
    LazyTicker.registerTask { server ->
        PlayerRegionEntryExitTracker.processTransitions(server)
    }
}
