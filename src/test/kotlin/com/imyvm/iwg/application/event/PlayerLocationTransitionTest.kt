package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.SubSpace
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlayerLocationTransitionTest {
    private val scopeA = scope("scope-A", 1, 0)
    private val scopeA2 = scope("scope-A2", 1, 1)
    private val scopeB = scope("scope-B", 2, 0)
    private val scopeC = scope("scope-C", 3, 0)
    private val regionA = Region("A", 1, mutableListOf(scopeA, scopeA2))
    private val regionB = Region("B", 2, mutableListOf(scopeB))
    private val regionC = Region("C", 3, mutableListOf(scopeC))
    private val subSpaceA = SubSpace(
        1L,
        "room",
        scopeA.requireAssignedScopeId(),
        Identifier.parse("minecraft:overworld"),
        GeoShape.rectangle(GeoPoint(10, 10), GeoPoint(30, 30))
    )

    @Test
    fun `first sample establishes baseline without transition output`() {
        val state = initialPlayerLocationState(PlayerLocation(regionA, scopeA), 100)

        assertSame(regionA, state.location.region)
        assertSame(scopeA, state.location.scope)
        assertEquals(100, state.stayStartedAt)
        assertNull(state.pendingExit)
        assertNull(state.scheduledEntryTitle)
    }

    @Test
    fun `direct region change keeps old scope paired with old region`() {
        val transition = calculateLocationTransition(state(regionA, scopeA, 10), PlayerLocation(regionB, scopeB), 30, 1_000)

        assertSame(regionA, transition.scopeExit?.region)
        assertSame(scopeA, transition.scopeExit?.scope)
        assertSame(regionB, transition.scopeEntry?.region)
        assertSame(scopeB, transition.scopeEntry?.scope)
        assertEquals(regionA to regionB, transition.regionEvent)
        assertEquals(StayPeriod(regionA, 10, 30), transition.completedStay)
    }

    @Test
    fun `rapid region changes replace delayed entry title`() {
        val toB = calculateLocationTransition(state(regionA, scopeA, 0), PlayerLocation(regionB, scopeB), 100, 1_000)
        val toC = calculateLocationTransition(toB.state, PlayerLocation(regionC, scopeC), 200, 1_000)

        assertSame(regionC, toC.state.scheduledEntryTitle?.region)
        assertEquals(200, toC.state.scheduledEntryTitle?.scheduledAt)
    }

    @Test
    fun `brief wilderness visit is debounced and restarts stay on return`() {
        val left = calculateLocationTransition(state(regionA, scopeA, 0), PlayerLocation(null, null), 100, 1_000)
        val returned = calculateLocationTransition(left.state, PlayerLocation(regionA, scopeA), 500, 1_000)

        assertNull(left.regionEvent)
        assertSame(regionA, left.state.location.region)
        assertEquals(StayPeriod(regionA, 0, 100), left.completedStay)
        assertNull(returned.regionEvent)
        assertNull(returned.state.pendingExit)
        assertEquals(500, returned.state.stayStartedAt)
    }

    @Test
    fun `wilderness exit commits after delay`() {
        val left = calculateLocationTransition(state(regionA, scopeA, 0), PlayerLocation(null, null), 100, 1_000)
        val expired = calculateLocationTransition(left.state, PlayerLocation(null, null), 1_100, 1_000)

        assertSame(regionA, expired.regionExit)
        assertEquals(regionA to null, expired.regionEvent)
        assertNull(expired.state.location.region)
    }

    @Test
    fun `entering another region while exit is pending commits one region change`() {
        val left = calculateLocationTransition(state(regionA, scopeA, 0), PlayerLocation(null, null), 100, 1_000)
        val entered = calculateLocationTransition(left.state, PlayerLocation(regionB, scopeB), 200, 1_000)

        assertSame(regionA, entered.regionExit)
        assertSame(regionB, entered.state.scheduledEntryTitle?.region)
        assertEquals(regionA to regionB, entered.regionEvent)
        assertSame(regionB, entered.incrementEntry)
        assertEquals(200, entered.state.stayStartedAt)
    }

    @Test
    fun `scope-only change leaves region stay untouched`() {
        val transition = calculateLocationTransition(state(regionA, scopeA, 10), PlayerLocation(regionA, scopeA2), 30, 1_000)

        assertNull(transition.regionEvent)
        assertNull(transition.completedStay)
        assertSame(regionA, transition.scopeExit?.region)
        assertSame(scopeA, transition.scopeExit?.scope)
        assertSame(scopeA2, transition.scopeEntry?.scope)
        assertEquals(10, transition.state.stayStartedAt)
    }


    @Test
    fun `subspace-only movement emits subspace entry and exit without scope event`() {
        val entered = calculateLocationTransition(state(regionA, scopeA, 10), PlayerLocation(regionA, scopeA, subSpaceA), 20, 1_000)

        assertNull(entered.scopeEntry)
        assertNull(entered.scopeExit)
        assertSame(subSpaceA, entered.subSpaceEntry?.subSpace)
        assertNull(entered.subSpaceExit)

        val left = calculateLocationTransition(entered.state, PlayerLocation(regionA, scopeA), 30, 1_000)

        assertNull(left.scopeEntry)
        assertNull(left.scopeExit)
        assertSame(subSpaceA, left.subSpaceExit?.subSpace)
        assertNull(left.subSpaceEntry)
    }

    @Test
    fun `entry from confirmed wilderness is immediate`() {
        val wilderness = PlayerLocationState(PlayerLocation(null, null))
        val entered = calculateLocationTransition(wilderness, PlayerLocation(regionA, scopeA), 20, 1_000)

        assertSame(regionA, entered.regionEntry)
        assertSame(regionA, entered.incrementEntry)
        assertEquals(null to regionA, entered.regionEvent)
        assertEquals(20, entered.state.stayStartedAt)
    }

    private fun state(region: Region, scope: GeoScope, startedAt: Long) = PlayerLocationState(
        PlayerLocation(region, scope),
        stayStartedAt = startedAt
    )

    private fun scope(name: String, regionId: Int, index: Int) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = com.imyvm.iwg.domain.component.ScopeId(
            com.imyvm.iwg.domain.component.generateCompatScopeIdRaw(
                regionId,
                index
            )
        )
    )
}
