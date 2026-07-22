package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoSubSpaceTransition
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SubSpaceTransitionEventTest {
    @AfterTest
    fun tearDown() {
        SubSpaceTransitionEvent.clearForTest()
    }

    @Test
    fun `publishes immutable subspace transition payload asynchronously`() {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(30, 30)),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        )
        val subSpace = SubSpace(
            1,
            "room",
            scope.requireAssignedScopeId(),
            scope.worldId,
            GeoShape.rectangle(GeoPoint(5, 5), GeoPoint(10, 10))
        )
        val region = Region("region", 7, mutableListOf(scope), subSpaces = mutableListOf(subSpace))
        val payload = WorldGeoSubSpaceTransition(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "tester",
            null,
            com.imyvm.iwg.application.space.WorldGeoSpaceSupport.snapshot(region, scope, subSpace),
            20L
        )
        val received = mutableListOf<WorldGeoSubSpaceTransition>()
        SubSpaceTransitionEvent.registerCallback { received.add(it) }

        SubSpaceTransitionEvent.publish(payload)
        SubSpaceTransitionEvent.awaitCallbacksForTest()

        assertEquals(listOf(payload), received)
        assertEquals("room", received.single().to?.name)
    }
}
