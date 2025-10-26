package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.application.region.permission.helper.hasPermissionBlacklist
import com.imyvm.iwg.util.text.Translator
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

fun playerContainerInteraction(
    player: PlayerEntity?,
    world: World?,
    hitResult: BlockHitResult?
): ActionResult {
    if (player == null || world == null || hitResult == null) return ActionResult.PASS

    val pos = hitResult.blockPos
    val blockEntity = world.getBlockEntity(pos)

    return if (blockEntity is NamedScreenHandlerFactory && !playerCanOpenContainer(player, pos)) {
        player.sendMessage(Translator.tr("setting.permission.container"))
        ActionResult.FAIL
    } else {
        ActionResult.PASS
    }
}

private fun playerCanOpenContainer(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermissionBlacklist(region, player.uuid, PermissionKey.CONTAINER, scope)
    }
    return true
}
