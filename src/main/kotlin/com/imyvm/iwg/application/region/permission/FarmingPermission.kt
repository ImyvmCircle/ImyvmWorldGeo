package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.denyPermissionAt
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FARMING
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

fun playerFarmingPermission() {
    PlayerBlockBreakEvents.BEFORE.register { world, player, pos, blockState, _ ->
        if (!isCropOnFarmland(world, pos, blockState.block)) return@register true
        if (denyPermissionAt(player, world, pos, PermissionKey.FARMING, PERMISSION_DEFAULT_FARMING.value,
                "setting.permission.farming")) return@register false
        true
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (stack.item !is BlockItem) return@register InteractionResult.PASS
        val cropBlock = (stack.item as BlockItem).block
        if (cropBlock !is CropBlock && cropBlock !is SweetBerryBushBlock) return@register InteractionResult.PASS
        val pos = hitResult.blockPos
        if (world.getBlockState(pos).block != Blocks.FARMLAND) return@register InteractionResult.PASS
        val placePos = pos.relative(hitResult.direction)
        if (denyPermissionAt(player, world, placePos, PermissionKey.FARMING, PERMISSION_DEFAULT_FARMING.value,
                "setting.permission.farming", hand == InteractionHand.MAIN_HAND)) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }

    registerBoneMealFarmingPermission()
}

internal fun isCropOnFarmland(world: Level, pos: BlockPos, block: Block): Boolean {
    if (block !is CropBlock && block !is SweetBerryBushBlock) return false
    return world.getBlockState(pos.below()).block == Blocks.FARMLAND
}

private fun registerBoneMealFarmingPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (stack.item !is BoneMealItem) return@register InteractionResult.PASS
        val pos = hitResult.blockPos
        if (!isCropOnFarmland(world, pos, world.getBlockState(pos).block)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, pos, PermissionKey.FARMING, PERMISSION_DEFAULT_FARMING.value,
                "setting.permission.farming", hand == InteractionHand.MAIN_HAND)) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
