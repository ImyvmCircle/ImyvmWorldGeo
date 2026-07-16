package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
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
    fun `entry from confirmed wilderness is immediate`() {
        val wilderness = PlayerLocationState(PlayerLocation(null, null))
        val entered = calculateLocationTransition(wilderness, PlayerLocation(regionA, scopeA), 20, 1_000)

        assertSame(regionA, entered.regionEntry)
        assertSame(regionA, entered.incrementEntry)
        assertEquals(null to regionA, entered.regionEvent)
        assertEquals(20, entered.state.stayStartedAt)
    }

    @Test
    fun `delete removes state when any delayed transition reference uses deleted region`() {
        val states = listOf(
            PlayerLocationState(PlayerLocation(regionA, scopeA), stayStartedAt = 10),
            PlayerLocationState(
                PlayerLocation(regionB, scopeB),
                pendingExit = PendingWildernessExit(regionA, 20)
            ),
            PlayerLocationState(
                PlayerLocation(regionB, scopeB),
                scheduledEntryTitle = ScheduledEntryTitle(regionA, 30)
            )
        )

        states.forEach { assertNull(it.removeIfReferencing(regionA)) }
    }

    @Test
    fun `delete keeps unrelated and detached same id states`() {
        val detachedSameId = Region("detachedA", regionA.numberID, mutableListOf(scope("detached", 1, 4)))
        val unrelated = PlayerLocationState(
            PlayerLocation(detachedSameId, detachedSameId.scopes.single()),
            pendingExit = PendingWildernessExit(regionB, 20),
            scheduledEntryTitle = ScheduledEntryTitle(regionC, 30),
            stayStartedAt = 10
        )

        assertSame(unrelated, unrelated.removeIfReferencing(regionA))
    }

    @Test
    fun `merge retargets every region reference and preserves scope identity and timing`() {
        val original = PlayerLocationState(
            PlayerLocation(regionA, scopeA),
            pendingExit = PendingWildernessExit(regionA, 20),
            scheduledEntryTitle = ScheduledEntryTitle(regionA, 30),
            stayStartedAt = 10
        )

        val retargeted = original.retargetRegion(regionA, regionB)

        assertSame(regionB, retargeted.location.region)
        assertSame(scopeA, retargeted.location.scope)
        assertSame(regionB, retargeted.pendingExit?.fromRegion)
        assertEquals(20, retargeted.pendingExit?.startedAt)
        assertSame(regionB, retargeted.scheduledEntryTitle?.region)
        assertEquals(30, retargeted.scheduledEntryTitle?.scheduledAt)
        assertEquals(10, retargeted.stayStartedAt)
    }

    @Test
    fun `merge ignores unrelated and detached same id references`() {
        val detachedSameId = Region("detachedA", regionA.numberID, mutableListOf(scope("detached", 1, 4)))
        val unrelated = PlayerLocationState(
            PlayerLocation(detachedSameId, detachedSameId.scopes.single()),
            pendingExit = PendingWildernessExit(regionB, 20),
            scheduledEntryTitle = ScheduledEntryTitle(regionC, 30),
            stayStartedAt = 10
        )

        assertSame(unrelated, unrelated.retargetRegion(regionA, regionB))
    }

    @Test
    fun `delete collection mutation prevents old state from surviving id reuse`() {
        val states = mutableMapOf(
            1 to PlayerLocationState(PlayerLocation(regionA, scopeA), stayStartedAt = 10),
            2 to PlayerLocationState(PlayerLocation(regionB, scopeB), stayStartedAt = 20)
        )

        states.removeStatesReferencing(regionA)
        val reusedRegion = Region("reused", regionA.numberID, mutableListOf(scope("reused", 1, 5)))
        states[3] = initialPlayerLocationState(PlayerLocation(reusedRegion, reusedRegion.scopes.single()), 30)

        assertNull(states[1])
        assertSame(regionB, states[2]?.location?.region)
        assertSame(reusedRegion, states[3]?.location?.region)
        assertEquals(30, states[3]?.stayStartedAt)
    }

    @Test
    fun `merge collection mutation makes later stay accounting target canonical region`() {
        val states = mutableMapOf(
            1 to PlayerLocationState(PlayerLocation(regionA, scopeA), stayStartedAt = 10),
            2 to PlayerLocationState(PlayerLocation(regionC, scopeC), stayStartedAt = 20)
        )

        states.retargetStates(regionA, regionB)

        assertSame(regionB, states[1]?.location?.region)
        assertEquals(10, states[1]?.stayStartedAt)
        assertSame(regionC, states[2]?.location?.region)
        assertEquals(20, states[2]?.stayStartedAt)
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
