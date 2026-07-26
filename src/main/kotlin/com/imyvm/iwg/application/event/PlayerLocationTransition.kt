package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope

internal data class PlayerLocation(val region: Region?, val scope: GeoScope?)

internal data class ScopedPlayerLocation(val region: Region, val scope: GeoScope)

internal sealed interface EntryPermissionTarget {
    val region: Region
    val locationName: String

    data class RegionTarget(override val region: Region) : EntryPermissionTarget {
        override val locationName: String
            get() = region.name
    }

    data class ScopeTarget(
        override val region: Region,
        val scope: GeoScope
    ) : EntryPermissionTarget {
        init {
            require(region.containsScope(scope)) { "scope does not belong to region" }
        }

        override val locationName: String
            get() = scope.scopeName
    }
}

internal data class ScheduledEntryTitle(val region: Region, val scheduledAt: Long)

internal data class PendingWildernessExit(val fromRegion: Region, val startedAt: Long)

internal data class PlayerLocationState(
    val location: PlayerLocation,
    val pendingExit: PendingWildernessExit? = null,
    val scheduledEntryTitle: ScheduledEntryTitle? = null,
    val stayStartedAt: Long? = null
)

internal fun PlayerLocationState.removeIfReferencing(region: Region): PlayerLocationState? {
    val referencesRegion = location.region === region ||
        pendingExit?.fromRegion === region ||
        scheduledEntryTitle?.region === region
    return if (referencesRegion) null else this
}

internal fun PlayerLocationState.retargetRegion(source: Region, target: Region): PlayerLocationState {
    val retargetedLocation = if (location.region === source) {
        location.copy(region = target)
    } else {
        location
    }
    val retargetedPendingExit = pendingExit?.let { pending ->
        if (pending.fromRegion === source) pending.copy(fromRegion = target) else pending
    }
    val retargetedEntryTitle = scheduledEntryTitle?.let { title ->
        if (title.region === source) title.copy(region = target) else title
    }
    if (
        retargetedLocation === location &&
        retargetedPendingExit === pendingExit &&
        retargetedEntryTitle === scheduledEntryTitle
    ) {
        return this
    }
    return copy(
        location = retargetedLocation,
        pendingExit = retargetedPendingExit,
        scheduledEntryTitle = retargetedEntryTitle
    )
}

internal fun <K> MutableMap<K, PlayerLocationState>.removeStatesReferencing(region: Region) {
    entries.removeIf { (_, state) -> state.removeIfReferencing(region) == null }
}

internal fun <K> MutableMap<K, PlayerLocationState>.retargetStates(source: Region, target: Region) {
    replaceAll { _, state -> state.retargetRegion(source, target) }
}

internal data class StayPeriod(val region: Region, val startedAt: Long, val endedAt: Long)

internal sealed interface RegionLocationChange {
    data class Entered(val region: Region) : RegionLocationChange
    data class Exited(val region: Region) : RegionLocationChange
    data class Moved(val from: Region, val to: Region) : RegionLocationChange
}

internal sealed interface ScopeLocationChange {
    data class Entered(val location: ScopedPlayerLocation) : ScopeLocationChange
    data class Exited(val location: ScopedPlayerLocation) : ScopeLocationChange
    data class Moved(
        val from: ScopedPlayerLocation,
        val to: ScopedPlayerLocation
    ) : ScopeLocationChange
}

internal sealed interface LocationTransitionEffect {
    data class RegionExitNotification(val region: Region) : LocationTransitionEffect
    data class RegionEntryNotification(val region: Region) : LocationTransitionEffect
    data class ScopeExitNotification(val location: ScopedPlayerLocation) : LocationTransitionEffect
    data class ScopeEntryNotification(val location: ScopedPlayerLocation) : LocationTransitionEffect
    data class EntryPermissionNotification(val target: EntryPermissionTarget) : LocationTransitionEffect
    data class StayCompleted(val period: StayPeriod) : LocationTransitionEffect
    data class RegionEntryIncrement(val region: Region) : LocationTransitionEffect
    data class RegionChanged(val change: RegionLocationChange) : LocationTransitionEffect
    data class ScopeChanged(val change: ScopeLocationChange) : LocationTransitionEffect
}

internal data class LocationTransition(
    val state: PlayerLocationState,
    val effects: List<LocationTransitionEffect> = emptyList()
)

internal fun initialPlayerLocationState(current: PlayerLocation, now: Long) = PlayerLocationState(
    location = current,
    stayStartedAt = now.takeIf { current.region != null }
)

internal fun calculateLocationTransition(
    previous: PlayerLocationState,
    current: PlayerLocation,
    now: Long,
    wildernessDelayMs: Long
): LocationTransition {
    val previousRegion = previous.location.region
    val currentRegion = current.region
    var committedRegion = previousRegion
    var pendingExit = previous.pendingExit
    var scheduledTitle = previous.scheduledEntryTitle
    var stayStartedAt = previous.stayStartedAt
    var completedStay: StayPeriod? = null

    if (sameRegion(currentRegion, previousRegion)) {
        if (pendingExit != null && currentRegion != null && stayStartedAt == null) {
            stayStartedAt = now
        }
        pendingExit = null
        committedRegion = currentRegion
    } else if (pendingExit != null) {
        if (currentRegion == null) {
            if (now - pendingExit.startedAt >= wildernessDelayMs) {
                committedRegion = null
                pendingExit = null
                scheduledTitle = null
            }
        } else {
            committedRegion = currentRegion
            pendingExit = null
            scheduledTitle = ScheduledEntryTitle(currentRegion, now)
            stayStartedAt = now
        }
    } else if (currentRegion == null) {
        if (previousRegion != null) {
            completedStay = stayStartedAt?.let { StayPeriod(previousRegion, it, now) }
            stayStartedAt = null
            pendingExit = PendingWildernessExit(previousRegion, now)
            scheduledTitle = null
        }
    } else {
        if (previousRegion != null) {
            completedStay = stayStartedAt?.let { StayPeriod(previousRegion, it, now) }
            scheduledTitle = ScheduledEntryTitle(currentRegion, now)
        }
        committedRegion = currentRegion
        stayStartedAt = now
    }

    val committedScope = if (sameRegion(currentRegion, committedRegion)) current.scope else null
    val newLocation = PlayerLocation(committedRegion, committedScope)
    val previousScoped = previous.location.toScopedLocation()
    val currentScoped = newLocation.toScopedLocation()
    val scopeChanged = previousScoped?.region?.numberID != currentScoped?.region?.numberID ||
        previousScoped?.scope !== currentScoped?.scope
    val regionChanged = previousRegion?.numberID != committedRegion?.numberID
    val regionChange = when {
        !regionChanged -> null
        previousRegion == null -> RegionLocationChange.Entered(checkNotNull(committedRegion))
        committedRegion == null -> RegionLocationChange.Exited(previousRegion)
        else -> RegionLocationChange.Moved(previousRegion, committedRegion)
    }
    val scopeChange = when {
        !scopeChanged -> null
        previousScoped == null -> ScopeLocationChange.Entered(checkNotNull(currentScoped))
        currentScoped == null -> ScopeLocationChange.Exited(previousScoped)
        else -> ScopeLocationChange.Moved(previousScoped, currentScoped)
    }
    val incrementEntry = committedRegion.takeIf { regionChanged }
    val permissionTarget = currentScoped
        ?.takeIf { scopeChanged }
        ?.let { EntryPermissionTarget.ScopeTarget(it.region, it.scope) }
        ?: incrementEntry?.let { EntryPermissionTarget.RegionTarget(it) }
    val hasEffects = regionChange != null ||
        scopeChange != null ||
        completedStay != null
    val effects = if (hasEffects) {
        buildList(9) {
            previousRegion?.takeIf { regionChanged }?.let {
                add(LocationTransitionEffect.RegionExitNotification(it))
            }
            committedRegion?.takeIf { regionChanged && previousRegion == null }?.let {
                add(LocationTransitionEffect.RegionEntryNotification(it))
            }
            previousScoped?.takeIf { scopeChanged }?.let {
                add(LocationTransitionEffect.ScopeExitNotification(it))
            }
            currentScoped?.takeIf { scopeChanged }?.let {
                add(LocationTransitionEffect.ScopeEntryNotification(it))
            }
            permissionTarget?.let {
                add(LocationTransitionEffect.EntryPermissionNotification(it))
            }
            completedStay?.let {
                add(LocationTransitionEffect.StayCompleted(it))
            }
            incrementEntry?.let {
                add(LocationTransitionEffect.RegionEntryIncrement(it))
            }
            regionChange?.let {
                add(LocationTransitionEffect.RegionChanged(it))
            }
            scopeChange?.let {
                add(LocationTransitionEffect.ScopeChanged(it))
            }
        }
    } else {
        emptyList()
    }

    return LocationTransition(
        state = PlayerLocationState(newLocation, pendingExit, scheduledTitle, stayStartedAt),
        effects = effects
    )
}

private fun sameRegion(left: Region?, right: Region?): Boolean = left?.numberID == right?.numberID

private fun PlayerLocation.toScopedLocation(): ScopedPlayerLocation? {
    val region = region ?: return null
    val scope = scope ?: return null
    return ScopedPlayerLocation(region, scope)
}
