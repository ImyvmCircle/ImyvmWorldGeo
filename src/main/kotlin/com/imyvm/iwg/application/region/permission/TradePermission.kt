package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_TRADE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader
import net.minecraft.world.InteractionResult

import net.minecraft.world.InteractionHand

fun playerTradePermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is Villager && entity !is WanderingTrader) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.TRADE, scope, PERMISSION_DEFAULT_TRADE.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.trade", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}