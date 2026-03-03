package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FARMING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.item.BlockItem
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

fun playerFarmingPermission() {
    PlayerBlockBreakEvents.BEFORE.register { world, player, pos, blockState, _ ->
        if (!isCropOnFarmland(world, pos, blockState.block)) return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FARMING, scope, PERMISSION_DEFAULT_FARMING.value)
            if (denial != null) {
                player.sendMessage(Translator.tr("setting.permission.farming", buildPermissionDenialContext(region, scope, denial)))
                return@register false
            }
        }
        true
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (stack.item !is BlockItem) return@register ActionResult.PASS
        val cropBlock = (stack.item as BlockItem).block
        if (cropBlock !is CropBlock && cropBlock !is SweetBerryBushBlock) return@register ActionResult.PASS
        val pos = hitResult.blockPos
        if (world.getBlockState(pos).block != Blocks.FARMLAND) return@register ActionResult.PASS
        val placePos = pos.offset(hitResult.side)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FARMING, scope, PERMISSION_DEFAULT_FARMING.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.farming", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}

internal fun isCropOnFarmland(world: World, pos: BlockPos, block: Block): Boolean {
    if (block !is CropBlock && block !is SweetBerryBushBlock) return false
    return world.getBlockState(pos.down()).block == Blocks.FARMLAND
}
