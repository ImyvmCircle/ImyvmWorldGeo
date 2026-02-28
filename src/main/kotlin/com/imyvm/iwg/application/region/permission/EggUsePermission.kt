package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_EGG_USE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.item.Items
import net.minecraft.util.TypedActionResult

fun playerEggUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.EGG)) return@register TypedActionResult.pass(stack)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, player.blockX, player.blockZ)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.EGG_USE, scope, PERMISSION_DEFAULT_EGG_USE.value)) {
                player.sendMessage(Translator.tr("setting.permission.egg_use"))
                return@register TypedActionResult.fail(stack)
            }
        }
        TypedActionResult.pass(stack)
    }
}
