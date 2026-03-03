package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_EGG_USE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult

fun playerEggUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.EGG)) return@register TypedActionResult.pass(stack)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, player.blockX, player.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.EGG_USE, scope, PERMISSION_DEFAULT_EGG_USE.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.egg_use", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register TypedActionResult.consume(stack)
            }
        }
        TypedActionResult.pass(stack)
    }
}
