package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_REDSTONE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.AbstractRedstoneGateBlock
import net.minecraft.block.BellBlock
import net.minecraft.block.ButtonBlock
import net.minecraft.block.DaylightDetectorBlock
import net.minecraft.block.LeverBlock
import net.minecraft.block.NoteBlock
import net.minecraft.util.ActionResult

fun playerRedstonePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (!isRedstoneDevice(block)) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.REDSTONE, scope, PERMISSION_DEFAULT_REDSTONE.value)) {
                player.sendMessage(Translator.tr("setting.permission.redstone"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}

private fun isRedstoneDevice(block: net.minecraft.block.Block): Boolean =
    block is ButtonBlock ||
    block is LeverBlock ||
    block is NoteBlock ||
    block is AbstractRedstoneGateBlock ||
    block is DaylightDetectorBlock ||
    block is BellBlock
