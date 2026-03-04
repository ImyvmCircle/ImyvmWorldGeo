package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_INTERACTION
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.AbstractCauldronBlock
import net.minecraft.block.BeehiveBlock
import net.minecraft.block.CakeBlock
import net.minecraft.block.CampfireBlock
import net.minecraft.block.CandleBlock
import net.minecraft.block.CandleCakeBlock
import net.minecraft.block.ChiseledBookshelfBlock
import net.minecraft.block.ComposterBlock
import net.minecraft.block.DecoratedPotBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.DragonEggBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.FlowerPotBlock
import net.minecraft.block.JukeboxBlock
import net.minecraft.block.LecternBlock
import net.minecraft.block.Oxidizable
import net.minecraft.block.RespawnAnchorBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.block.VaultBlock
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.AxeItem
import net.minecraft.item.HoneycombItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.ShovelItem
import net.minecraft.registry.tag.BlockTags
import net.minecraft.block.Blocks
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

fun playerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val blockState = world.getBlockState(pos)
        val block = blockState.block
        val stack = player.getStackInHand(hand)
        if (!isInteractiveBlock(block) && !isToolStateChangeInteraction(stack, block, blockState)) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.INTERACTION, scope, PERMISSION_DEFAULT_INTERACTION.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.interaction", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}

private fun isInteractiveBlock(block: net.minecraft.block.Block): Boolean =
    block is DoorBlock ||
    block is TrapdoorBlock ||
    block is FenceGateBlock ||
    block is LecternBlock ||
    block is ChiseledBookshelfBlock ||
    block is CakeBlock ||
    block is CandleCakeBlock ||
    block is CandleBlock ||
    block is FlowerPotBlock ||
    block is ComposterBlock ||
    block is DragonEggBlock ||
    block is RespawnAnchorBlock ||
    block is JukeboxBlock ||
    block is AbstractCauldronBlock ||
    block is BeehiveBlock ||
    block is VaultBlock ||
    block is CampfireBlock ||
    block is DecoratedPotBlock

private fun isToolStateChangeInteraction(stack: ItemStack, block: net.minecraft.block.Block, blockState: net.minecraft.block.BlockState): Boolean {
    val item = stack.item
    return when {
        item is AxeItem && block is Oxidizable -> true
        item is AxeItem && HoneycombItem.WAXED_TO_UNWAXED_BLOCKS.get().containsKey(block) -> true
        item is AxeItem && blockState.isIn(BlockTags.LOGS) -> true
        item is HoneycombItem && HoneycombItem.UNWAXED_TO_WAXED_BLOCKS.get().containsKey(block) -> true
        item is ShovelItem && (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) -> true
        item is ShovelItem && block is CampfireBlock -> true
        isWaterBottle(stack) && (block == Blocks.DIRT || block == Blocks.ROOTED_DIRT) -> true
        else -> false
    }
}

private fun isWaterBottle(stack: ItemStack): Boolean =
    stack.isOf(Items.POTION) && (stack.get(DataComponentTypes.POTION_CONTENTS)?.let { !it.hasEffects() } ?: false)
