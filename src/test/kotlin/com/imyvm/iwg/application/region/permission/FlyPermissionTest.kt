package com.imyvm.iwg.application.region.permission

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FlyPermissionTest {
    @Test
    fun `deadline is absolute and independent of lazy interval`() {
        assertEquals(1_100L, tickDeadline(1_000L, 5))
        assertEquals(1_000L, tickDeadline(1_000L, 0))
        assertFailsWith<IllegalArgumentException> { tickDeadline(1_000L, -1) }
    }

    @Test
    fun `survival permission grants only when no other source already allows flight`() {
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                FlyAbilityChange.ENABLE,
                FlyNotification.ENABLED
            ),
            transition(state = null, canFlyHere = true, mayfly = false)
        )
        assertEquals(
            ManagedFlyTransition(null, recovery = null),
            transition(state = null, canFlyHere = true, mayfly = true)
        )
    }

    @Test
    fun `leaving permission starts countdown and returning restores grant`() {
        val pending = transition(ManagedFlyState.Granted, canFlyHere = false, currentTick = 100)
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.PendingDisable(200),
                ManagedFlightRecovery.OWNED_FLIGHT,
                notification = FlyNotification.DISABLED_SOON
            ),
            pending
        )
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                notification = FlyNotification.RESTORED
            ),
            transition(pending.state, ManagedFlightRecovery.OWNED_FLIGHT, canFlyHere = true)
        )
    }

    @Test
    fun `expired and zero countdown enter landing protection`() {
        val expected = ManagedFlyTransition(
            ManagedFlyState.LandingProtection,
            ManagedFlightRecovery.LANDING_REQUIRED,
            FlyAbilityChange.DISABLE,
            FlyNotification.DISABLED
        )
        assertEquals(
            expected,
            transition(
                ManagedFlyState.PendingDisable(200),
                ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = false,
                currentTick = 200
            )
        )
        assertEquals(
            expected,
            transition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = false,
                countdownSeconds = 0
            )
        )
    }

    @Test
    fun `vanilla flight suspends runtime owner and removes recovery marker`() {
        assertEquals(
            ManagedFlyTransition(ManagedFlyState.SuspendedByVanilla, recovery = null),
            transition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = true,
                vanillaOwnsFlight = true
            )
        )
        assertEquals(
            ManagedFlyTransition(ManagedFlyState.SuspendedByVanilla, recovery = null),
            transition(
                ManagedFlyState.PendingDisable(200),
                ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = false,
                vanillaOwnsFlight = true
            )
        )
    }

    @Test
    fun `vanilla flight discards crash recovery without touching abilities`() {
        assertEquals(
            ManagedFlyTransition(null, recovery = null),
            transition(
                state = null,
                recovery = ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = true,
                vanillaOwnsFlight = true
            )
        )
        assertEquals(
            ManagedFlyTransition(null, recovery = null),
            transition(
                state = ManagedFlyState.LandingProtection,
                recovery = ManagedFlightRecovery.LANDING_REQUIRED,
                canFlyHere = false,
                vanillaOwnsFlight = true
            )
        )
    }

    @Test
    fun `returning from vanilla mode restores only where permission allows`() {
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                FlyAbilityChange.ENABLE,
                FlyNotification.RESTORED
            ),
            transition(
                ManagedFlyState.SuspendedByVanilla,
                canFlyHere = true,
                mayfly = false
            )
        )
        assertNull(
            transition(
                ManagedFlyState.SuspendedByVanilla,
                canFlyHere = false,
                mayfly = false
            ).state
        )
    }

    @Test
    fun `owned flight crash snapshot is reconciled from current permission`() {
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                notification = FlyNotification.RESTORED
            ),
            transition(
                state = null,
                recovery = ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = true
            )
        )
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.LandingProtection,
                ManagedFlightRecovery.LANDING_REQUIRED,
                FlyAbilityChange.DISABLE,
                FlyNotification.DISABLED
            ),
            transition(
                state = null,
                recovery = ManagedFlightRecovery.OWNED_FLIGHT,
                canFlyHere = false,
                safelyLanded = false
            )
        )
    }

    @Test
    fun `landing recovery survives arbitrary time and clears only after safe landing`() {
        val state = ManagedFlyState.LandingProtection
        assertEquals(
            ManagedFlyTransition(state, ManagedFlightRecovery.LANDING_REQUIRED),
            transition(
                state,
                ManagedFlightRecovery.LANDING_REQUIRED,
                canFlyHere = false,
                safelyLanded = false,
                currentTick = Long.MAX_VALUE
            )
        )
        assertEquals(
            ManagedFlyTransition(null, recovery = null),
            transition(
                state,
                ManagedFlightRecovery.LANDING_REQUIRED,
                canFlyHere = false,
                safelyLanded = true
            )
        )
    }

    @Test
    fun `permission restored during landing protection becomes owned flight`() {
        assertEquals(
            ManagedFlyTransition(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                FlyAbilityChange.ENABLE,
                FlyNotification.RESTORED
            ),
            transition(
                ManagedFlyState.LandingProtection,
                ManagedFlightRecovery.LANDING_REQUIRED,
                canFlyHere = true,
                mayfly = false
            )
        )
    }

    @Test
    fun `recovered landing marker already on ground is disabled and removed`() {
        assertEquals(
            ManagedFlyTransition(null, null, FlyAbilityChange.DISABLE),
            transition(
                state = null,
                recovery = ManagedFlightRecovery.LANDING_REQUIRED,
                canFlyHere = false,
                mayfly = true,
                safelyLanded = true
            )
        )
    }

    @Test
    fun `negative countdown fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            transition(state = null, canFlyHere = false, countdownSeconds = -1)
        }
    }

    @Test
    fun `session state cleanup isolates one player and clears all on shutdown`() {
        val firstPlayer = UUID.randomUUID()
        val secondPlayer = UUID.randomUUID()
        val store = ManagedFlyStateStore()
        store.update(firstPlayer, ManagedFlyState.Granted)
        store.update(secondPlayer, ManagedFlyState.SuspendedByVanilla)

        store.clear(firstPlayer)
        assertNull(store[firstPlayer])
        assertEquals(ManagedFlyState.SuspendedByVanilla, store[secondPlayer])

        store.clear()
        assertNull(store[secondPlayer])
    }

    @Test
    fun `disconnect converts owned flight into persistent landing recovery`() {
        assertEquals(
            ManagedFlyDisconnectTransition(ManagedFlightRecovery.LANDING_REQUIRED, true),
            disconnect(ManagedFlyState.Granted, ManagedFlightRecovery.OWNED_FLIGHT)
        )
        assertEquals(
            ManagedFlyDisconnectTransition(ManagedFlightRecovery.LANDING_REQUIRED, true),
            disconnect(ManagedFlyState.PendingDisable(200), ManagedFlightRecovery.OWNED_FLIGHT)
        )
        assertEquals(
            ManagedFlyDisconnectTransition(ManagedFlightRecovery.LANDING_REQUIRED, false),
            disconnect(ManagedFlyState.LandingProtection, ManagedFlightRecovery.LANDING_REQUIRED, mayfly = false)
        )
    }

    @Test
    fun `disconnect never revokes vanilla or unrelated flight`() {
        assertEquals(
            ManagedFlyDisconnectTransition(null, false),
            disconnect(ManagedFlyState.Granted, ManagedFlightRecovery.OWNED_FLIGHT, vanillaOwnsFlight = true)
        )
        assertEquals(
            ManagedFlyDisconnectTransition(null, false),
            disconnect(state = null, recovery = null, mayfly = true)
        )
    }

    @Test
    fun `death revokes inherited mod flight but not vanilla or unrelated flight`() {
        assertEquals(
            true,
            shouldDisableManagedFlightAfterDeath(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                vanillaOwnsFlight = false
            )
        )
        assertEquals(
            true,
            shouldDisableManagedFlightAfterDeath(
                state = null,
                recovery = ManagedFlightRecovery.OWNED_FLIGHT,
                vanillaOwnsFlight = false
            )
        )
        assertEquals(
            false,
            shouldDisableManagedFlightAfterDeath(
                ManagedFlyState.Granted,
                ManagedFlightRecovery.OWNED_FLIGHT,
                vanillaOwnsFlight = true
            )
        )
        assertEquals(
            false,
            shouldDisableManagedFlightAfterDeath(
                state = null,
                recovery = null,
                vanillaOwnsFlight = false
            )
        )
    }

    private fun transition(
        state: ManagedFlyState?,
        recovery: ManagedFlightRecovery? = null,
        canFlyHere: Boolean,
        vanillaOwnsFlight: Boolean = false,
        mayfly: Boolean = true,
        safelyLanded: Boolean = false,
        currentTick: Long = 100,
        countdownSeconds: Int = 5
    ) = transitionManagedFly(
        state,
        recovery,
        canFlyHere,
        vanillaOwnsFlight,
        mayfly,
        safelyLanded,
        currentTick,
        countdownSeconds
    )

    private fun disconnect(
        state: ManagedFlyState?,
        recovery: ManagedFlightRecovery?,
        vanillaOwnsFlight: Boolean = false,
        mayfly: Boolean = true
    ) = transitionManagedFlyOnDisconnect(state, recovery, vanillaOwnsFlight, mayfly)
}
