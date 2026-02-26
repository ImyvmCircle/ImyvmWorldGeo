package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_TOGGLE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.util.ActionResult

fun playerTogglePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (block !is DoorBlock && block !is TrapdoorBlock && block !is FenceGateBlock) {
            return@register ActionResult.PASS
        }
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.TOGGLE, scope, PERMISSION_DEFAULT_TOGGLE.value)) {
                player.sendMessage(Translator.tr("setting.permission.toggle"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}
