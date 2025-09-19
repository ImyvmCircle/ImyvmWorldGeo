package com.imyvm.iwg.util.setting

import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.domain.Region
import java.util.*

fun hasPermissionDefaultAllow(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: Region.Companion.GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: true
}

fun hasPermissionDefaultDeny(
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
    scope?.settings?.filterIsInstance<com.imyvm.iwg.domain.PermissionSetting>()?.find { it.key == key }?.let {
        return if (it.isPersonal) it.playerUUID == playerUUID && it.value else it.value
    }
    region.settings.filterIsInstance<com.imyvm.iwg.domain.PermissionSetting>().find { it.key == key }?.let {
        return if (it.isPersonal) it.playerUUID == playerUUID && it.value else it.value
    }
    return null
}