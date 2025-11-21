package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.application.region.permission.helper.hasPermissionBlacklist
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

fun playerBuildPermission(){
    UseBlockCallback.EVENT.register { player, _, _, hitResult ->
        val pos = hitResult.blockPos
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register ActionResult.FAIL
        }
        ActionResult.PASS
    }
}

fun playerBreakPermission(){
    PlayerBlockBreakEvents.BEFORE.register { _, player, pos, _, _ ->
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.break"))
            return@register false
        }
        true
    }
}

private fun playerCanBuildOrBreak(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermissionBlacklist(region, player.uuid, PermissionKey.BUILD_BREAK, scope)
    }
    return true
}