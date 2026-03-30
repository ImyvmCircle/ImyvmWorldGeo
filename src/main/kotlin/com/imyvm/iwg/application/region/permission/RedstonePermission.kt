package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_REDSTONE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.world.level.block.DiodeBlock
import net.minecraft.world.level.block.BellBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.DaylightDetectorBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.InteractionResult

import net.minecraft.world.InteractionHand

fun playerRedstonePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (!isRedstoneDevice(block)) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.REDSTONE, scope, PERMISSION_DEFAULT_REDSTONE.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.redstone", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}

private fun isRedstoneDevice(block: net.minecraft.world.level.block.Block): Boolean =
    block is ButtonBlock ||
    block is LeverBlock ||
    block is NoteBlock ||
    block is DiodeBlock ||
    block is DaylightDetectorBlock ||
    block is BellBlock