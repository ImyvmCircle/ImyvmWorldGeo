package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_CONTAINER
import com.imyvm.iwg.util.text.Translator
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

    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level(), pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.CONTAINER, scope, PERMISSION_DEFAULT_CONTAINER.value)
        if (denial != null) {
            if (hand == InteractionHand.MAIN_HAND) {
                player.sendSystemMessage(Translator.tr("setting.permission.container", buildPermissionDenialContext(region, scope, denial))!!)
            }
            return InteractionResult.CONSUME
        }
    }
    return InteractionResult.PASS
}