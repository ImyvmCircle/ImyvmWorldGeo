package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.PermissionDenialSource
import com.imyvm.iwg.application.region.permission.helper.buildScopePermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.buildSubSpacePermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getScopePermissionDenialSource
import com.imyvm.iwg.application.region.permission.helper.getSubSpacePermissionDenialSource
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
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

internal data class PermissionDenialAt(
    val region: Region,
    val scope: GeoScope,
    val subSpace: SubSpace?,
    val source: PermissionDenialSource
) {
    fun context(): String = subSpace?.let { buildSubSpacePermissionDenialContext(region, scope, it, source) }
        ?: buildScopePermissionDenialContext(region, scope, source)
}

internal fun denyPermissionAt(
    player: Player,
    world: Level,
    pos: BlockPos,
    key: PermissionKey,
    defaultValue: Boolean,
    messageKey: String
): Boolean {
    if (world.isClientSide) return false
    val denial = findPermissionDenialAt(world, pos, player.uuid, key, defaultValue) ?: return false
    player.sendSystemMessage(Translator.tr(messageKey, denial.context())!!)
    return true
}

internal fun findPermissionDenial(
    region: Region,
    scope: GeoScope,
    subSpace: SubSpace?,
    playerUuid: java.util.UUID,
    key: PermissionKey,
    defaultValue: Boolean
): PermissionDenialAt? {
    val source = if (subSpace == null) {
        getScopePermissionDenialSource(region, scope, playerUuid, key, defaultValue)
    } else {
        getSubSpacePermissionDenialSource(region, scope, subSpace, playerUuid, key, defaultValue)
    } ?: return null
    return PermissionDenialAt(region, scope, subSpace, source)
}

internal fun findPermissionDenialAt(
    world: Level,
    pos: BlockPos,
    playerUuid: java.util.UUID,
    key: PermissionKey,
    defaultValue: Boolean
): PermissionDenialAt? {
    val (region, scope, subSpace) = RegionDatabase.getRegionScopeSubSpaceAt(world, pos.x, pos.z) ?: return null
    return findPermissionDenial(region, scope, subSpace, playerUuid, key, defaultValue)
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
