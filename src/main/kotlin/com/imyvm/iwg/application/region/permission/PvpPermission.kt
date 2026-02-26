package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_PVP
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult

fun playerPvpPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
        if (entity !is PlayerEntity) return@register ActionResult.PASS
        val target = entity as PlayerEntity
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, target.blockX, target.blockZ)
        regionAndScope?.let { (region, scope) ->
            val attackerHasPvp = hasPermission(region, player.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            val defenderHasPvp = hasPermission(region, target.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            if (!attackerHasPvp || !defenderHasPvp) {
                player.sendMessage(Translator.tr("setting.permission.pvp"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}
