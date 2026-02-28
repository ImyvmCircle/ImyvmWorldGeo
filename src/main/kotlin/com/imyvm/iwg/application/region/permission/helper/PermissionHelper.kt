package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.PermissionSetting
import java.util.*

sealed class PermissionDenialSource {
    object AtScope : PermissionDenialSource()
    object AtRegion : PermissionDenialSource()
    object ByDefault : PermissionDenialSource()
}

fun hasPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: defaultValue
}

fun getPermissionDenialSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): PermissionDenialSource? {
    val result = checkPermissionWithSource(region, playerUUID, key, scope)
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

private fun checkPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope?
): Boolean? = checkPermissionWithSource(region, playerUUID, key, scope)?.first

private fun checkPermissionWithSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope?
): Pair<Boolean, PermissionDenialSource>? {
    val ancestors = buildList {
        var current = key.parent
        while (current != null) {
            add(current)
            current = current.parent
        }
        reverse()
    }
    for (ancestor in ancestors) {
        val result = checkExplicitPermissionWithSource(region, playerUUID, ancestor, scope)
        if (result != null) return result
    }
    return checkExplicitPermissionWithSource(region, playerUUID, key, scope)
}

private fun checkExplicitPermissionWithSource(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope?
): Pair<Boolean, PermissionDenialSource>? {
    scope?.settings?.filterIsInstance<PermissionSetting>()?.let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value to PermissionDenialSource.AtScope
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value to PermissionDenialSource.AtScope
        }
    }
    region.settings.filterIsInstance<PermissionSetting>().let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value to PermissionDenialSource.AtRegion
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value to PermissionDenialSource.AtRegion
        }
    }
    return null
}