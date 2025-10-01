package com.imyvm.iwg.application.regionapp

import com.imyvm.iwg.RegionDatabase
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.util.setting.hasPermissionBlacklist
import com.imyvm.iwg.util.ui.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

fun registerPlayerBuildBreakPermission(){
    playerBuildPermission()
    playerBreakPermission()
}

private fun playerBuildPermission(){
    UseBlockCallback.EVENT.register { player, _, _, hitResult ->
        val pos = hitResult.blockPos
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register ActionResult.FAIL
        }
        ActionResult.PASS
    }
}

private fun playerBreakPermission(){
    PlayerBlockBreakEvents.BEFORE.register { _, player, pos, _, _ ->
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.break"))
            return@register false
        }
        true
    }
}

private fun playerCanBuildOrBreak(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermissionBlacklist(region, player.uuid, PermissionKey.BUILD_BREAK, scope)
    }
    return true
}