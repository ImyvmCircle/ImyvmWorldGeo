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
import net.minecraft.block.CandleCakeBlock
import net.minecraft.block.ChiseledBookshelfBlock
import net.minecraft.block.ComposterBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.DragonEggBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.FlowerPotBlock
import net.minecraft.block.JukeboxBlock
import net.minecraft.block.LecternBlock
import net.minecraft.block.RespawnAnchorBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.block.VaultBlock
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

fun playerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (!isInteractiveBlock(block)) return@register ActionResult.PASS
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
    block is FlowerPotBlock ||
    block is ComposterBlock ||
    block is DragonEggBlock ||
    block is RespawnAnchorBlock ||
    block is JukeboxBlock ||
    block is AbstractCauldronBlock ||
    block is BeehiveBlock ||
    block is VaultBlock
