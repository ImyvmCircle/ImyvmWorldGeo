package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.PermissionSetting
import java.util.*

fun hasPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null,
    defaultValue: Boolean = true
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: defaultValue
}

fun hasPermissionBlacklist(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: true
}

fun hasPermissionWhitelist(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: false
}

private fun checkPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: GeoScope?
): Boolean? {
    scope?.settings?.filterIsInstance<PermissionSetting>()?.let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value
        }
    }
    region.settings.filterIsInstance<PermissionSetting>().let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value
        }
    }

    return null
}