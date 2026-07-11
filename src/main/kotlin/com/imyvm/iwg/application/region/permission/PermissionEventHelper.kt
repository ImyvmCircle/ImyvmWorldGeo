package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

internal fun denyPermissionAt(
    player: Player,
    world: Level,
    pos: BlockPos,
    key: PermissionKey,
    defaultValue: Boolean,
    messageKey: String
): Boolean {
    if (world.isClientSide) return false
    val (region, scope) = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return false
    val denial = getPermissionDenialSource(region, player.uuid, key, scope, defaultValue) ?: return false
    player.sendSystemMessage(Translator.tr(messageKey, buildPermissionDenialContext(region, scope, denial))!!)
    return true
}
