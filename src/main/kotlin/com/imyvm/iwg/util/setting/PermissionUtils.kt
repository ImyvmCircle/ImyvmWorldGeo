package com.imyvm.iwg.util.setting

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.domain.Region
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.*

fun playerCanBuildOrBreak(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermissionDefaultAllow(region, player.uuid, PermissionKey.BUILD_BREAK, scope)
    }
    return true
}

private fun hasPermissionDefaultAllow(
    region: Region,
    playerUUID: UUID,
    key: PermissionKey,
    scope: Region.Companion.GeoScope? = null
): Boolean {
    return checkPermission(region, playerUUID, key, scope) ?: true
}

private fun hasPermissionDefaultDeny(
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