package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope

internal data class PlayerLocation(val region: Region?, val scope: GeoScope?)

internal data class ScopedPlayerLocation(val region: Region, val scope: GeoScope)

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

internal data class LocationTransition(
    val state: PlayerLocationState,
    val regionExit: Region? = null,
    val regionEntry: Region? = null,
    val scopeExit: ScopedPlayerLocation? = null,
    val scopeEntry: ScopedPlayerLocation? = null,
    val completedStay: StayPeriod? = null,
    val incrementEntry: Region? = null,
    val regionEvent: Pair<Region?, Region?>? = null,
    val scopeEvent: Pair<ScopedPlayerLocation?, ScopedPlayerLocation?>? = null
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
    var regionExit: Region? = null
    var regionEntry: Region? = null
    var completedStay: StayPeriod? = null
    var incrementEntry: Region? = null

    if (sameRegion(currentRegion, previousRegion)) {
        if (pendingExit != null && currentRegion != null && stayStartedAt == null) {
            stayStartedAt = now
        }
        pendingExit = null
        committedRegion = currentRegion
    } else if (pendingExit != null) {
        if (currentRegion == null) {
            if (now - pendingExit.startedAt >= wildernessDelayMs) {
                regionExit = pendingExit.fromRegion
                committedRegion = null
                pendingExit = null
                scheduledTitle = null
            }
        } else {
            regionExit = pendingExit.fromRegion
            committedRegion = currentRegion
            pendingExit = null
            scheduledTitle = ScheduledEntryTitle(currentRegion, now)
            incrementEntry = currentRegion
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
        if (previousRegion == null) {
            regionEntry = currentRegion
        } else {
            regionExit = previousRegion
            completedStay = stayStartedAt?.let { StayPeriod(previousRegion, it, now) }
            scheduledTitle = ScheduledEntryTitle(currentRegion, now)
        }
        committedRegion = currentRegion
        incrementEntry = currentRegion
        stayStartedAt = now
    }

    val committedScope = if (sameRegion(currentRegion, committedRegion)) current.scope else null
    val newLocation = PlayerLocation(committedRegion, committedScope)
    val previousScoped = previous.location.toScopedLocation()
    val currentScoped = newLocation.toScopedLocation()
    val scopeChanged = previousScoped?.region?.numberID != currentScoped?.region?.numberID ||
        previousScoped?.scope !== currentScoped?.scope
    val regionChanged = previousRegion?.numberID != committedRegion?.numberID

    return LocationTransition(
        state = PlayerLocationState(newLocation, pendingExit, scheduledTitle, stayStartedAt),
        regionExit = regionExit,
        regionEntry = regionEntry,
        scopeExit = previousScoped.takeIf { scopeChanged },
        scopeEntry = currentScoped.takeIf { scopeChanged },
        completedStay = completedStay,
        incrementEntry = incrementEntry,
        regionEvent = (previousRegion to committedRegion).takeIf { regionChanged },
        scopeEvent = (previousScoped to currentScoped).takeIf { scopeChanged }
    )
}

private fun sameRegion(left: Region?, right: Region?): Boolean = left?.numberID == right?.numberID

private fun PlayerLocation.toScopedLocation(): ScopedPlayerLocation? {
    val region = region ?: return null
    val scope = scope ?: return null
    return ScopedPlayerLocation(region, scope)
}
