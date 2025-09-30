package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.util.setting.hasPermissionDefaultAllow
import com.imyvm.iwg.util.ui.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

fun registerPlayerContainerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, _, hitResult ->
        val pos = hitResult.blockPos
        val blockEntity = world.getBlockEntity(pos)

        if (blockEntity is NamedScreenHandlerFactory) {
            if (!playerCanOpenContainer(player, pos)) {
                player.sendMessage(Translator.tr("setting.permission.container"))
                return@register ActionResult.FAIL
            }
        }

        ActionResult.PASS
    }
}

private fun playerCanOpenContainer(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = ImyvmWorldGeo.data.getRegionAndScopeAt(pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermissionDefaultAllow(region, player.uuid, PermissionKey.CONTAINER, scope)
    }
    return true
}