package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_REDSTONE
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
        if (denyPermissionAt(player, world, pos, PermissionKey.REDSTONE, PERMISSION_DEFAULT_REDSTONE.value,
                "setting.permission.redstone")) return@register InteractionResult.CONSUME
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
