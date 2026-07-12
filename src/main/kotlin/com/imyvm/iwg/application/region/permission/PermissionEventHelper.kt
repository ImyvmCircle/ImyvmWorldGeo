package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildScopePermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getScopePermissionDenialSource
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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
    val denial = getScopePermissionDenialSource(region, scope, player.uuid, key, defaultValue) ?: return false
    player.sendSystemMessage(Translator.tr(messageKey, buildScopePermissionDenialContext(region, scope, denial))!!)
    return true
}

internal fun adjacentTarget(pos: BlockPos, direction: Direction): BlockPos = pos.relative(direction)

internal fun igniteTarget(pos: BlockPos, direction: Direction, lightsClickedBlock: Boolean): BlockPos =
    if (lightsClickedBlock) pos else adjacentTarget(pos, direction)
