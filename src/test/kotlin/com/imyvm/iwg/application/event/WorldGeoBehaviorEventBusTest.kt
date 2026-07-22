package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.WorldGeoBehaviorEvent
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGeoBehaviorEventBusTest {
    @AfterTest
    fun tearDown() {
        WorldGeoBehaviorEventBus.clearForTest()
    }

    @Test
    fun `publish stores recent event and notifies callback`() {
        val received = mutableListOf<WorldGeoBehaviorEvent>()
        WorldGeoBehaviorEventBus.registerCallback { received.add(it) }
        val event = event(WorldGeoBehaviorType.DEBUG_TEST, 1)

        WorldGeoBehaviorEventBus.publish(event)
        WorldGeoBehaviorEventBus.awaitCallbacksForTest()

        assertEquals(listOf(event), received)
        assertEquals(listOf(event), WorldGeoBehaviorEventBus.getRecentEvents())
    }

    @Test
    fun `recent events keeps bounded newest window`() {
        repeat(25) { index -> WorldGeoBehaviorEventBus.publish(event(WorldGeoBehaviorType.DEBUG_TEST, index)) }

        val recent = WorldGeoBehaviorEventBus.getRecentEvents()

        assertEquals(20, recent.size)
        assertEquals(5, recent.first().x)
        assertEquals(listOf(23, 24), WorldGeoBehaviorEventBus.getRecentEvents(2).map { it.x })
    }

    private fun event(type: WorldGeoBehaviorType, x: Int) = WorldGeoBehaviorEvent(
        type = type,
        playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        playerName = "tester",
        dimensionId = Identifier.parse("minecraft:overworld"),
        x = x,
        y = 64,
        z = 0,
        unixMillis = 1_700_000_000_000L
    )
}
