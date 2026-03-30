package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FARMING
import com.imyvm.iwg.util.text.Translator
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
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FARMING, scope, PERMISSION_DEFAULT_FARMING.value)
            if (denial != null) {
                player.sendSystemMessage(Translator.tr("setting.permission.farming", buildPermissionDenialContext(region, scope, denial))!!)
                return@register false
            }
        }
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
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FARMING, scope, PERMISSION_DEFAULT_FARMING.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.farming", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
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
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FARMING, scope, PERMISSION_DEFAULT_FARMING.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.farming", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}