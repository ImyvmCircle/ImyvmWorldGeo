package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_WIND_CHARGE_USE
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult


fun playerWindChargeUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.WIND_CHARGE)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.WIND_CHARGE_USE,
                PERMISSION_DEFAULT_WIND_CHARGE_USE.value, "setting.permission.wind_charge_use")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
