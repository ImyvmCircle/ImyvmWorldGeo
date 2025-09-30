package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.util.setting.playerCanOpenContainer
import com.imyvm.iwg.util.ui.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.util.ActionResult

fun registerPlayerContainerInteractionPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
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