package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ITEM_FRAME
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult

fun playerItemFramePermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrame) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.ITEM_FRAME,
                PERMISSION_DEFAULT_ITEM_FRAME.value, "setting.permission.item_frame")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.ITEM_FRAME) && !stack.`is`(Items.GLOW_ITEM_FRAME)) return@register InteractionResult.PASS
        val pos = hitResult.blockPos
        if (denyPermissionAt(player, world, pos, PermissionKey.ITEM_FRAME,
                PERMISSION_DEFAULT_ITEM_FRAME.value, "setting.permission.item_frame")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrame) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.ITEM_FRAME,
                PERMISSION_DEFAULT_ITEM_FRAME.value, "setting.permission.item_frame")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
