package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_FISHING
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult

fun playerFishingPermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.FISHING_ROD)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.RPG_FISHING,
                PERMISSION_DEFAULT_RPG_FISHING.value, "setting.permission.fishing")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
