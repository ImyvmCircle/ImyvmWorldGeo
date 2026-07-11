package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.denyPermissionAt
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_CONTAINER
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.MenuProvider
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.Level

fun playerContainerInteraction(
    player: Player?,
    world: Level?,
    hand: InteractionHand?,
    hitResult: BlockHitResult?
): InteractionResult {
    if (player == null || world == null || hitResult == null) return InteractionResult.PASS

    val pos = hitResult.blockPos
    val blockEntity = world.getBlockEntity(pos)
    if (blockEntity !is MenuProvider) return InteractionResult.PASS

    if (denyPermissionAt(player, world, pos, PermissionKey.CONTAINER, PERMISSION_DEFAULT_CONTAINER.value,
            "setting.permission.container", hand == InteractionHand.MAIN_HAND)) return InteractionResult.CONSUME
    return InteractionResult.PASS
}
