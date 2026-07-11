package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_TRADE
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader
import net.minecraft.world.InteractionResult


fun playerTradePermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is Villager && entity !is WanderingTrader) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.TRADE,
                PERMISSION_DEFAULT_TRADE.value, "setting.permission.trade")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
