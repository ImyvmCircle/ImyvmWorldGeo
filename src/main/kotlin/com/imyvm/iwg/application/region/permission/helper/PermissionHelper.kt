package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.*
import java.util.*

sealed class PermissionDenialSource {
    data object AtScope : PermissionDenialSource()
    data object AtRegion : PermissionDenialSource()
    data object ByDefault : PermissionDenialSource()
}

fun hasPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): Boolean {
    return resolvePermissionSettingValue(region, scope, playerUUID, key) ?: defaultValue
}

fun getPermissionDenialSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): PermissionDenialSource? {
    val result = resolvePermissionWithSource(region, scope, playerUUID, key)
    return when {
        result == null -> if (defaultValue) null else PermissionDenialSource.ByDefault
        result.first -> null
        else -> result.second
    }
}

fun buildPermissionDenialContext(region: Region, scope: GeoScope?, source: PermissionDenialSource): String {
    return if (source == PermissionDenialSource.AtScope && scope != null) {
        "Scope &b${scope.scopeName}&7 of Region &b${region.name}&7"
    } else {
        "Region &b${region.name}&7"
    }
}

fun resolvePermissionSettingValue(
    region: Region,
    scope: GeoScope?,
    playerUUID: UUID?,
    key: BaseKey
): Boolean? = resolvePermissionWithSource(region, scope, playerUUID, key)?.first

private fun resolvePermissionWithSource(
    region: Region,
    scope: GeoScope?,
    playerUUID: UUID?,
    key: BaseKey
): Pair<Boolean, PermissionDenialSource>? {
    val explicit = resolveExplicitPermission(region, scope, playerUUID, key)
    if (explicit != null) return explicit
    if (key is PermissionKey) {
        var ancestor = key.parent
        while (ancestor != null) {
            resolveExplicitPermission(region, scope, playerUUID, ancestor)?.let { return it }
            ancestor = ancestor.parent
        }
    }
    return null
}

private fun resolveExplicitPermission(
    region: Region,
    scope: GeoScope?,
    playerUUID: UUID?,
    key: BaseKey
): Pair<Boolean, PermissionDenialSource>? {
    findPermission(scope?.settings, playerUUID, key)?.let {
        return it to PermissionDenialSource.AtScope
    }
    findPermission(region.settings, playerUUID, key)?.let {
        return it to PermissionDenialSource.AtRegion
    }
    return null
}

private fun findPermission(settings: List<Setting>?, playerUUID: UUID?, key: BaseKey): Boolean? {
    val permissions = settings.orEmpty()
    if (playerUUID != null) {
        permissions.firstOrNull {
            (it is PermissionSetting || it is ExtensionPermissionSetting) &&
                    it.key == key && it.playerUUID == playerUUID
        }?.let {
            return it.value as Boolean
        }
    }
    return permissions.firstOrNull {
        (it is PermissionSetting || it is ExtensionPermissionSetting) && it.key == key && !it.isPersonal
    }?.value as Boolean?
}
