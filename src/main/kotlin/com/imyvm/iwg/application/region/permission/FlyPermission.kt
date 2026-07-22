package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasScopePermission
import com.imyvm.iwg.application.region.permission.helper.hasSubSpacePermission
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FLY
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

internal sealed interface ManagedFlyState {
    data object Granted : ManagedFlyState
    data class PendingDisable(val expiresAtTick: Long) : ManagedFlyState
    data object SuspendedByVanilla : ManagedFlyState
    data object LandingProtection : ManagedFlyState
}

internal enum class FlyAbilityChange {
    NONE,
    ENABLE,
    DISABLE
}

internal enum class FlyNotification {
    NONE,
    ENABLED,
    RESTORED,
    DISABLED_SOON,
    DISABLED
}

internal data class ManagedFlyTransition(
    val state: ManagedFlyState?,
    val recovery: ManagedFlightRecovery?,
    val abilityChange: FlyAbilityChange = FlyAbilityChange.NONE,
    val notification: FlyNotification = FlyNotification.NONE
)

internal data class ManagedFlyDisconnectTransition(
    val recovery: ManagedFlightRecovery?,
    val disableAbilities: Boolean
)

internal class ManagedFlyStateStore {
    private val states: MutableMap<UUID, ManagedFlyState> = mutableMapOf()

    operator fun get(playerId: UUID): ManagedFlyState? = states[playerId]

    fun update(playerId: UUID, state: ManagedFlyState?) {
        if (state == null) states.remove(playerId)
        else states[playerId] = state
    }

    fun clear(playerId: UUID) {
        states.remove(playerId)
    }

    fun clear() {
        states.clear()
    }
}

private val managedFlyStates = ManagedFlyStateStore()

fun managePlayersFly(server: MinecraftServer) {
    val currentTick = server.overworld().gameTime
    for (player in getOnlinePlayers(server)) {
        processPlayerFly(player, currentTick)
    }
}

fun processPlayerFly(player: ServerPlayer) {
    processPlayerFly(player, player.level().server.overworld().gameTime)
}

private fun processPlayerFly(player: ServerPlayer, currentTick: Long) {
    val uuid = player.uuid
    val resolved = RegionDatabase.getRegionScopeSubSpaceAt(
        player.level(),
        player.blockPosition().x,
        player.blockPosition().z
    )
    val canFlyHere = resolved?.let { (region, scope, subSpace) ->
        if (subSpace == null) {
            hasScopePermission(region, scope, uuid, PermissionKey.FLY, PERMISSION_DEFAULT_FLY.value)
        } else {
            hasSubSpacePermission(region, scope, subSpace, uuid, PermissionKey.FLY, PERMISSION_DEFAULT_FLY.value)
        }
    } ?: false
    val transition = transitionManagedFly(
        state = managedFlyStates[uuid],
        recovery = player.getManagedFlightRecovery(),
        canFlyHere = canFlyHere,
        vanillaOwnsFlight = player.isCreative || player.isSpectator,
        mayfly = player.abilities.mayfly,
        safelyLanded = player.onGround(),
        currentTick = currentTick,
        countdownSeconds = PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value
    )

    applyFlyTransition(player, transition)
    managedFlyStates.update(uuid, transition.state)
}

private fun applyFlyTransition(player: ServerPlayer, transition: ManagedFlyTransition) {
    player.setManagedFlightRecovery(transition.recovery)
    when (transition.abilityChange) {
        FlyAbilityChange.NONE -> Unit
        FlyAbilityChange.ENABLE -> {
            player.abilities.mayfly = true
            player.onUpdateAbilities()
        }
        FlyAbilityChange.DISABLE -> {
            player.abilities.mayfly = false
            player.abilities.flying = false
            player.onUpdateAbilities()
        }
    }

    val message = when (transition.notification) {
        FlyNotification.NONE -> null
        FlyNotification.ENABLED -> Translator.tr("setting.permission.fly.enabled")
        FlyNotification.RESTORED -> Translator.tr("setting.permission.fly.restored")
        FlyNotification.DISABLED_SOON -> Translator.tr(
            "setting.permission.fly.disabled.soon",
            PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS.value
        )
        FlyNotification.DISABLED -> Translator.tr("setting.permission.fly.disabled")
    }
    message?.let(player::sendSystemMessage)
}

internal fun transitionManagedFly(
    state: ManagedFlyState?,
    recovery: ManagedFlightRecovery?,
    canFlyHere: Boolean,
    vanillaOwnsFlight: Boolean,
    mayfly: Boolean,
    safelyLanded: Boolean,
    currentTick: Long,
    countdownSeconds: Int
): ManagedFlyTransition {
    require(countdownSeconds >= 0) { "countdown duration must not be negative" }

    if (vanillaOwnsFlight) {
        val suspendedState = when (state) {
            ManagedFlyState.Granted,
            is ManagedFlyState.PendingDisable,
            ManagedFlyState.SuspendedByVanilla -> ManagedFlyState.SuspendedByVanilla
            ManagedFlyState.LandingProtection,
            null -> null
        }
        return ManagedFlyTransition(suspendedState, recovery = null)
    }

    if (state == null && recovery != null) {
        if (canFlyHere) return grantFlyTransition(mayfly, FlyNotification.RESTORED)
        if (safelyLanded) {
            return ManagedFlyTransition(null, null, FlyAbilityChange.DISABLE)
        }
        return landingProtectionTransition(FlyNotification.DISABLED)
    }

    return when (state) {
        null -> when {
            !canFlyHere || mayfly -> ManagedFlyTransition(null, recovery = null)
            else -> grantFlyTransition(mayfly, FlyNotification.ENABLED)
        }
        ManagedFlyState.Granted -> when {
            canFlyHere -> grantFlyTransition(mayfly)
            countdownSeconds == 0 -> landingProtectionTransition(FlyNotification.DISABLED)
            else -> ManagedFlyTransition(
                ManagedFlyState.PendingDisable(tickDeadline(currentTick, countdownSeconds)),
                ManagedFlightRecovery.OWNED_FLIGHT,
                notification = FlyNotification.DISABLED_SOON
            )
        }
        is ManagedFlyState.PendingDisable -> when {
            canFlyHere -> grantFlyTransition(mayfly, FlyNotification.RESTORED)
            currentTick >= state.expiresAtTick -> landingProtectionTransition(FlyNotification.DISABLED)
            else -> ManagedFlyTransition(state, ManagedFlightRecovery.OWNED_FLIGHT)
        }
        ManagedFlyState.SuspendedByVanilla -> when {
            !canFlyHere -> ManagedFlyTransition(null, recovery = null)
            else -> grantFlyTransition(mayfly, FlyNotification.RESTORED)
        }
        ManagedFlyState.LandingProtection -> when {
            canFlyHere -> grantFlyTransition(mayfly, FlyNotification.RESTORED)
            safelyLanded -> ManagedFlyTransition(null, recovery = null)
            else -> ManagedFlyTransition(
                ManagedFlyState.LandingProtection,
                ManagedFlightRecovery.LANDING_REQUIRED
            )
        }
    }
}

private fun grantFlyTransition(
    mayfly: Boolean,
    notification: FlyNotification = FlyNotification.NONE
) = ManagedFlyTransition(
    ManagedFlyState.Granted,
    ManagedFlightRecovery.OWNED_FLIGHT,
    if (mayfly) FlyAbilityChange.NONE else FlyAbilityChange.ENABLE,
    notification
)

private fun landingProtectionTransition(notification: FlyNotification) = ManagedFlyTransition(
    ManagedFlyState.LandingProtection,
    ManagedFlightRecovery.LANDING_REQUIRED,
    FlyAbilityChange.DISABLE,
    notification
)

/**
 * Maintains crash-safe landing protection until the player reaches the ground.
 *
 * [currentTick] is retained for source/JVM compatibility with existing callers; landing protection
 * is no longer time-limited.
 */
@Suppress("UNUSED_PARAMETER")
fun processFallImmunity(player: ServerPlayer, currentTick: Long) {
    processLandingProtection(player)
}

private fun processLandingProtection(player: ServerPlayer) {
    if (managedFlyStates[player.uuid] != ManagedFlyState.LandingProtection) return
    player.fallDistance = 0.0
    if (player.onGround()) {
        managedFlyStates.clear(player.uuid)
        player.setManagedFlightRecovery(null)
    }
}

@Deprecated("Use the Long tick overload", ReplaceWith("processFallImmunity(player, currentTick.toLong())"))
fun processFallImmunity(player: ServerPlayer, currentTick: Int) =
    processFallImmunity(player, currentTick.toLong())

internal fun tickDeadline(currentTick: Long, seconds: Int): Long {
    require(seconds >= 0) { "duration must not be negative" }
    return Math.addExact(currentTick, seconds.toLong() * 20L)
}

internal fun handleFlyDisconnect(player: ServerPlayer) {
    val transition = transitionManagedFlyOnDisconnect(
        managedFlyStates[player.uuid],
        player.getManagedFlightRecovery(),
        player.isCreative || player.isSpectator,
        player.abilities.mayfly
    )
    player.setManagedFlightRecovery(transition.recovery)
    if (transition.disableAbilities) {
        player.abilities.mayfly = false
        player.abilities.flying = false
    }
    managedFlyStates.clear(player.uuid)
}

internal fun transitionManagedFlyOnDisconnect(
    state: ManagedFlyState?,
    recovery: ManagedFlightRecovery?,
    vanillaOwnsFlight: Boolean,
    mayfly: Boolean
): ManagedFlyDisconnectTransition {
    if (vanillaOwnsFlight) return ManagedFlyDisconnectTransition(null, disableAbilities = false)
    return when {
        state == ManagedFlyState.Granted || state is ManagedFlyState.PendingDisable ||
            recovery == ManagedFlightRecovery.OWNED_FLIGHT -> ManagedFlyDisconnectTransition(
            ManagedFlightRecovery.LANDING_REQUIRED,
            disableAbilities = true
        )
        state == ManagedFlyState.LandingProtection || recovery == ManagedFlightRecovery.LANDING_REQUIRED ->
            ManagedFlyDisconnectTransition(
                ManagedFlightRecovery.LANDING_REQUIRED,
                disableAbilities = mayfly
            )
        else -> ManagedFlyDisconnectTransition(null, disableAbilities = false)
    }
}

internal fun handleFlyRespawn(oldPlayer: ServerPlayer, newPlayer: ServerPlayer, alive: Boolean) {
    if (!alive) {
        val state = managedFlyStates[oldPlayer.uuid]
        if (shouldDisableManagedFlightAfterDeath(
                state,
                oldPlayer.getManagedFlightRecovery(),
                newPlayer.isCreative || newPlayer.isSpectator
            )
        ) {
            newPlayer.abilities.mayfly = false
            newPlayer.abilities.flying = false
        }
        managedFlyStates.clear(oldPlayer.uuid)
        newPlayer.setManagedFlightRecovery(null)
    }
    processPlayerFly(newPlayer)
}

internal fun shouldDisableManagedFlightAfterDeath(
    state: ManagedFlyState?,
    recovery: ManagedFlightRecovery?,
    vanillaOwnsFlight: Boolean
): Boolean = !vanillaOwnsFlight && (
    state == ManagedFlyState.Granted ||
        state is ManagedFlyState.PendingDisable ||
        recovery == ManagedFlightRecovery.OWNED_FLIGHT
    )

internal fun clearFlySessionState() {
    managedFlyStates.clear()
}
