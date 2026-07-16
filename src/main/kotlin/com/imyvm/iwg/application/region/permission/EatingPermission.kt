package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_EATING
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult

fun playerEatingPermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (stack.get(DataComponents.FOOD) == null) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.RPG_EATING,
                PERMISSION_DEFAULT_RPG_EATING.value, "setting.permission.eating")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
