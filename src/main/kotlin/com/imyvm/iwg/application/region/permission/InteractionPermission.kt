package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_INTERACTION
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.world.level.block.AbstractCauldronBlock
import net.minecraft.world.level.block.BeehiveBlock
import net.minecraft.world.level.block.CakeBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.ChiseledBookShelfBlock
import net.minecraft.world.level.block.ComposterBlock
import net.minecraft.world.level.block.DecoratedPotBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.DragonEggBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.LecternBlock
import net.minecraft.world.level.block.WeatheringCopper
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.VaultBlock
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.HoneycombItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ShovelItem
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

fun playerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val blockState = world.getBlockState(pos)
        val block = blockState.block
        val stack = player.getItemInHand(hand)
        if (!isInteractiveBlock(block) && !isToolStateChangeInteraction(stack, block, blockState)) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.INTERACTION, scope, PERMISSION_DEFAULT_INTERACTION.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.interaction", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}

private fun isInteractiveBlock(block: net.minecraft.world.level.block.Block): Boolean =
    block is DoorBlock ||
    block is TrapDoorBlock ||
    block is FenceGateBlock ||
    block is LecternBlock ||
    block is ChiseledBookShelfBlock ||
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

private fun isToolStateChangeInteraction(stack: ItemStack, block: net.minecraft.world.level.block.Block, blockState: net.minecraft.world.level.block.state.BlockState): Boolean {
    val item = stack.item
    return when {
        item is AxeItem && block is WeatheringCopper -> true
        item is AxeItem && HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(block) -> true
        item is AxeItem && blockState.`is`(BlockTags.LOGS) -> true
        item is HoneycombItem && HoneycombItem.WAXABLES.get().containsKey(block) -> true
        item is ShovelItem && (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) -> true
        item is ShovelItem && block is CampfireBlock -> true
        isWaterBottle(stack) && (block == Blocks.DIRT || block == Blocks.ROOTED_DIRT) -> true
        else -> false
    }
}

private fun isWaterBottle(stack: ItemStack): Boolean =
    stack.`is`(Items.POTION) && (stack.get(DataComponents.POTION_CONTENTS)?.let { !it.hasEffects() } ?: false)