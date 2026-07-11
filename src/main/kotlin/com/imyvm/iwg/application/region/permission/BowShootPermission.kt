package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_BOW_SHOOT
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult

fun playerBowShootPermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.BOW) && !stack.`is`(Items.CROSSBOW)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, player.blockPosition(), PermissionKey.RPG_BOW_SHOOT,
                PERMISSION_DEFAULT_RPG_BOW_SHOOT.value, "setting.permission.bow_shoot")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
