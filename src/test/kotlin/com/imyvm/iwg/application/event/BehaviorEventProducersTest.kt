package com.imyvm.iwg.application.event

import kotlin.test.Test
import kotlin.test.assertEquals

class BehaviorEventProducersTest {
    @Test
    fun `combat target id keeps player identity but collapses mob instances`() {
        assertEquals("minecraft:zombie", normalizedCombatTargetId("minecraft:zombie", "mob-uuid", false))
        assertEquals("player-uuid", normalizedCombatTargetId("minecraft:player", "player-uuid", true))
    }
}
