package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildScopePermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getScopePermissionDenialSource
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.TntBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

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

internal fun blockPlacementTarget(
    player: Player,
    hand: InteractionHand,
    stack: ItemStack,
    hitResult: BlockHitResult
): BlockPos = BlockPlaceContext(player, hand, stack, hitResult).clickedPos

internal fun filledBucketTarget(
    clickedPos: BlockPos,
    direction: Direction,
    clickedAcceptsContents: Boolean,
    isCrouching: Boolean
): BlockPos = if (clickedAcceptsContents && !isCrouching) {
    clickedPos
} else {
    adjacentTarget(clickedPos, direction)
}

internal fun igniteTarget(pos: BlockPos, direction: Direction, state: BlockState): BlockPos =
    if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) ||
        CandleCakeBlock.canLight(state) || state.block is TntBlock
    ) {
        pos
    } else {
        adjacentTarget(pos, direction)
    }
