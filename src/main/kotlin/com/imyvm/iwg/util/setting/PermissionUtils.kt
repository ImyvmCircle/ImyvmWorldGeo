package com.imyvm.iwg.util.setting

import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.domain.Region
import java.util.*

fun hasPermissionBlacklist(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: Region.Companion.GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: true
}

fun hasPermissionWhitelist(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: Region.Companion.GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: false
}

private fun checkPermission(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: Region.Companion.GeoScope?
): Boolean? {
    scope?.settings?.filterIsInstance<com.imyvm.iwg.domain.PermissionSetting>()?.let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value
        }
    }
    region.settings.filterIsInstance<com.imyvm.iwg.domain.PermissionSetting>().let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let {
            return it.value
        }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let {
            return it.value
        }
    }

    return null
}