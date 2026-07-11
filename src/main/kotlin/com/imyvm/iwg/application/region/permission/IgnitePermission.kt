package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_IGNITE
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CandleCakeBlock

fun playerIgnitePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.FLINT_AND_STEEL) && !stack.`is`(Items.FIRE_CHARGE)) return@register InteractionResult.PASS
        val clickedPos = hitResult.blockPos
        val state = world.getBlockState(clickedPos)
        val lightsClickedBlock = CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)
        val pos = igniteTarget(clickedPos, hitResult.direction, lightsClickedBlock)
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
