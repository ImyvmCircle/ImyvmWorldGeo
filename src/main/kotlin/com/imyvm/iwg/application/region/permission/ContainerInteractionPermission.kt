package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_CONTAINER
import com.imyvm.iwg.util.text.Translator
import net.minecraft.util.Hand
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World

fun playerContainerInteraction(
    player: PlayerEntity?,
    world: World?,
    hand: Hand?,
    hitResult: BlockHitResult?
): ActionResult {
    if (player == null || world == null || hitResult == null) return ActionResult.PASS

    val pos = hitResult.blockPos
    val blockEntity = world.getBlockEntity(pos)
    if (blockEntity !is NamedScreenHandlerFactory) return ActionResult.PASS

    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.CONTAINER, scope, PERMISSION_DEFAULT_CONTAINER.value)
        if (denial != null) {
            if (hand == Hand.MAIN_HAND) {
                player.sendMessage(Translator.tr("setting.permission.container", buildPermissionDenialContext(region, scope, denial)))
            }
            return ActionResult.CONSUME
        }
    }
    return ActionResult.PASS
}
