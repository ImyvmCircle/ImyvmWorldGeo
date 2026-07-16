package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_SNOWBALL_USE
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult


fun playerSnowballUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.SNOWBALL)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.SNOWBALL_USE,
                PERMISSION_DEFAULT_SNOWBALL_USE.value, "setting.permission.snowball_use")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
