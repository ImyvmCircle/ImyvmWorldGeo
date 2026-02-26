package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_TRADE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.WanderingTraderEntity
import net.minecraft.util.ActionResult

fun playerTradePermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
        if (entity !is VillagerEntity && entity !is WanderingTraderEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.TRADE, scope, PERMISSION_DEFAULT_TRADE.value)) {
                player.sendMessage(Translator.tr("setting.permission.trade"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}
