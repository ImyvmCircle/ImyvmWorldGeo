package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FISHING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

fun playerFishingPermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.FISHING_ROD)) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, player.blockPosition().x, player.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.FISHING_, scope, PERMISSION_DEFAULT_FISHING.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.fishing", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}
