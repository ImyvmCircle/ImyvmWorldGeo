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
            require(region.geometryScope.contains(scope)) { "scope does not belong to region" }
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

internal fun resolveRegionGlobalPermission(region: Region, key: BaseKey): ResolvedPermission? =
    resolvePermission(PermissionTarget.RegionOnly(region), PermissionSubject.Global, key)

internal fun resolveRegionPlayerPermission(region: Region, playerUUID: UUID, key: BaseKey): ResolvedPermission? =
    resolvePermission(PermissionTarget.RegionOnly(region), PermissionSubject.Player(playerUUID), key)

internal fun resolveScopeGlobalPermission(region: Region, scope: GeoScope, key: BaseKey): ResolvedPermission? =
    resolvePermission(PermissionTarget.ScopeOverride(region, scope), PermissionSubject.Global, key)

internal fun resolveScopePlayerPermission(
    region: Region,
    scope: GeoScope,
    playerUUID: UUID,
    key: BaseKey
): ResolvedPermission? = resolvePermission(
    PermissionTarget.ScopeOverride(region, scope),
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
    key: BaseKey
): ResolvedPermission? {
    resolveExplicitPermission(target, subject, key)?.let { return it }
    if (key is PermissionKey) {
        var ancestor = key.parent
        while (ancestor != null) {
            resolveExplicitPermission(target, subject, ancestor)?.let { return it }
            ancestor = ancestor.parent
        }
    }
    return null
}

private fun resolveExplicitPermission(
    target: PermissionTarget,
    subject: PermissionSubject,
    key: BaseKey
): ResolvedPermission? {
    if (target is PermissionTarget.ScopeOverride) {
        findPermission(target.scope.settings, subject, key)?.let {
            return ResolvedPermission(it, PermissionDenialSource.AtScope)
        }
    }
    findPermission(target.region.settings, subject, key)?.let {
        return ResolvedPermission(it, PermissionDenialSource.AtRegion)
    }
    return null
}

private fun findPermission(settings: List<Setting>, subject: PermissionSubject, key: BaseKey): Boolean? {
    if (subject is PermissionSubject.Player) {
        settings.firstOrNull {
            it.isPermissionFor(key) && it.playerUUID == subject.uuid
        }?.let { return it.value as Boolean }
    }
    return settings.firstOrNull { it.isPermissionFor(key) && !it.isPersonal }?.value as Boolean?
}

private fun Setting.isPermissionFor(key: BaseKey): Boolean =
    (this is PermissionSetting || this is ExtensionPermissionSetting) && this.key == key
