package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.*
import java.util.UUID

sealed class PermissionDenialSource {
    data object AtScope : PermissionDenialSource()
    data object AtRegion : PermissionDenialSource()
    data object ByDefault : PermissionDenialSource()
}

internal data class ResolvedPermission(
    val value: Boolean,
    val source: PermissionDenialSource
)

private sealed interface PermissionTarget {
    val region: Region

    data class RegionOnly(override val region: Region) : PermissionTarget
    data class ScopeOverride(override val region: Region, val scope: GeoScope) : PermissionTarget {
        init {
            require(region.containsScope(scope)) { "scope does not belong to region" }
        }
    }
    data class SubSpaceOverride(
        override val region: Region,
        val scope: GeoScope,
        val subSpace: SubSpace
    ) : PermissionTarget {
        init {
            require(region.containsScope(scope)) { "scope does not belong to region" }
            require(region.containsSubSpace(subSpace)) { "subspace does not belong to region" }
            require(subSpace.parentScopeId == scope.requireAssignedScopeId()) { "subspace does not belong to scope" }
        }
    }
}

private sealed interface PermissionSubject {
    data object Global : PermissionSubject
    data class Player(val uuid: UUID) : PermissionSubject
}

fun hasRegionPermission(region: Region, playerUUID: UUID, key: PermissionKey, defaultValue: Boolean = true): Boolean =
    getRegionPermissionDenialSource(region, playerUUID, key, defaultValue) == null

fun hasScopePermission(
    region: Region,
    scope: GeoScope,
    playerUUID: UUID,
    key: PermissionKey,
    defaultValue: Boolean = true
): Boolean = getScopePermissionDenialSource(region, scope, playerUUID, key, defaultValue) == null

fun getRegionPermissionDenialSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    defaultValue: Boolean = true
): PermissionDenialSource? = denialSource(
    resolvePermission(PermissionTarget.RegionOnly(region), PermissionSubject.Player(playerUUID), key),
    defaultValue
)

fun getScopePermissionDenialSource(
    region: Region,
    scope: GeoScope,
    playerUUID: UUID,
    key: PermissionKey,
    defaultValue: Boolean = true
): PermissionDenialSource? = denialSource(
    resolvePermission(PermissionTarget.ScopeOverride(region, scope), PermissionSubject.Player(playerUUID), key),
    defaultValue
)

internal fun resolveRegionGlobalPermission(region: Region, key: PermissionKeyLike): ResolvedPermission? =
    resolvePermission(PermissionTarget.RegionOnly(region), PermissionSubject.Global, key)

internal fun resolveRegionPlayerPermission(region: Region, playerUUID: UUID, key: PermissionKeyLike): ResolvedPermission? =
    resolvePermission(PermissionTarget.RegionOnly(region), PermissionSubject.Player(playerUUID), key)

internal fun resolveScopeGlobalPermission(region: Region, scope: GeoScope, key: PermissionKeyLike): ResolvedPermission? =
    resolvePermission(PermissionTarget.ScopeOverride(region, scope), PermissionSubject.Global, key)

internal fun resolveScopePlayerPermission(
    region: Region,
    scope: GeoScope,
    playerUUID: UUID,
    key: PermissionKeyLike
): ResolvedPermission? = resolvePermission(
    PermissionTarget.ScopeOverride(region, scope),
    PermissionSubject.Player(playerUUID),
    key
)

internal fun resolveSubSpaceGlobalPermission(
    region: Region,
    scope: GeoScope,
    subSpace: SubSpace,
    key: PermissionKeyLike
): ResolvedPermission? = resolvePermission(
    PermissionTarget.SubSpaceOverride(region, scope, subSpace),
    PermissionSubject.Global,
    key
)

internal fun resolveSubSpacePlayerPermission(
    region: Region,
    scope: GeoScope,
    subSpace: SubSpace,
    playerUUID: UUID,
    key: PermissionKeyLike
): ResolvedPermission? = resolvePermission(
    PermissionTarget.SubSpaceOverride(region, scope, subSpace),
    PermissionSubject.Player(playerUUID),
    key
)

@Deprecated("Use hasRegionPermission or hasScopePermission")
fun hasPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): Boolean = if (scope == null) {
    hasRegionPermission(region, playerUUID, key, defaultValue)
} else {
    hasScopePermission(region, scope, playerUUID, key, defaultValue)
}

@Deprecated("Use getRegionPermissionDenialSource or getScopePermissionDenialSource")
fun getPermissionDenialSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): PermissionDenialSource? = if (scope == null) {
    getRegionPermissionDenialSource(region, playerUUID, key, defaultValue)
} else {
    getScopePermissionDenialSource(region, scope, playerUUID, key, defaultValue)
}

fun buildScopePermissionDenialContext(region: Region, scope: GeoScope, source: PermissionDenialSource): String {
    return if (source == PermissionDenialSource.AtScope) {
        "Scope &b${scope.scopeName}&7 of Region &b${region.name}&7"
    } else {
        "Region &b${region.name}&7"
    }
}

@Deprecated("Use buildScopePermissionDenialContext or the region name directly")
fun buildPermissionDenialContext(region: Region, scope: GeoScope?, source: PermissionDenialSource): String =
    if (scope == null) "Region &b${region.name}&7" else buildScopePermissionDenialContext(region, scope, source)

private fun denialSource(result: ResolvedPermission?, defaultValue: Boolean): PermissionDenialSource? = when {
    result == null -> if (defaultValue) null else PermissionDenialSource.ByDefault
    result.value -> null
    else -> result.source
}

private fun resolvePermission(
    target: PermissionTarget,
    subject: PermissionSubject,
    key: PermissionKeyLike
): ResolvedPermission? {
    resolveExplicitPermission(target, subject, key)?.let { return it }
    if (key !is PermissionKey) return null
    var ancestor = key.parent
    while (ancestor != null) {
        resolveExplicitPermission(target, subject, ancestor)?.let { return it }
        ancestor = ancestor.parent
    }
    return null
}

private fun resolveExplicitPermission(
    target: PermissionTarget,
    subject: PermissionSubject,
    key: PermissionKeyLike
): ResolvedPermission? {
    if (target is PermissionTarget.SubSpaceOverride) {
        findPermission(target.subSpace.settingStore, subject, key)?.let {
            return ResolvedPermission(it, PermissionDenialSource.AtScope)
        }
    }
    if (target is PermissionTarget.ScopeOverride) {
        findPermission(target.scope.settingStore, subject, key)?.let {
            return ResolvedPermission(it, PermissionDenialSource.AtScope)
        }
    }
    if (target is PermissionTarget.SubSpaceOverride) {
        findPermission(target.scope.settingStore, subject, key)?.let {
            return ResolvedPermission(it, PermissionDenialSource.AtScope)
        }
    }
    findPermission(target.region.settingStore, subject, key)?.let {
        return ResolvedPermission(it, PermissionDenialSource.AtRegion)
    }
    return null
}

private fun findPermission(store: SettingStore, subject: PermissionSubject, key: PermissionKeyLike): Boolean? {
    if (subject is PermissionSubject.Player) {
        store.playerPermission(key, subject.uuid)?.let { return it }
    }
    return store.globalPermission(key)
}
