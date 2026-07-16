package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_POTION_USE
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult


fun playerPotionUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.SPLASH_POTION) && !stack.`is`(Items.LINGERING_POTION)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.POTION_USE,
                PERMISSION_DEFAULT_POTION_USE.value, "setting.permission.potion_use")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
