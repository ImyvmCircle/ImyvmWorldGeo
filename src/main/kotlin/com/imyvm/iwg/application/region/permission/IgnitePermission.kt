package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_IGNITE
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult

fun playerIgnitePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.FLINT_AND_STEEL) && !stack.`is`(Items.FIRE_CHARGE)) return@register InteractionResult.PASS
        val clickedPos = hitResult.blockPos
        val state = world.getBlockState(clickedPos)
        val pos = igniteTarget(clickedPos, hitResult.direction, state)
        if (denyPermissionAt(player, world, pos, PermissionKey.IGNITE,
                PERMISSION_DEFAULT_IGNITE.value, "setting.permission.ignite")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.FLINT_AND_STEEL) && !stack.`is`(Items.FIRE_CHARGE)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.IGNITE,
                PERMISSION_DEFAULT_IGNITE.value, "setting.permission.ignite")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
