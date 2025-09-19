package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.util.setting.playerCanBuildOrBreak
import com.imyvm.iwg.util.ui.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.util.ActionResult

fun registerPlayerBuildBreakPermission(){
    playerBuildPermission()
    playerBreakPermission()
}

private fun playerBuildPermission(){
    UseBlockCallback.EVENT.register { player, world, _, hitResult ->
        val pos = hitResult.blockPos
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register ActionResult.FAIL
        }
        ActionResult.PASS
    }
}

private fun playerBreakPermission(){
    PlayerBlockBreakEvents.BEFORE.register { world, player, pos, _, _ ->
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.break"))
            return@register false
        }
        true
    }
}