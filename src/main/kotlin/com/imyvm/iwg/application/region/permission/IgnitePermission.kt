package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_IGNITE
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

fun playerIgnitePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.FLINT_AND_STEEL) && !stack.isOf(Items.FIRE_CHARGE)) return@register ActionResult.PASS
        val pos = hitResult.blockPos
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.IGNITE, scope, PERMISSION_DEFAULT_IGNITE.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.ignite", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.FLINT_AND_STEEL) && !stack.isOf(Items.FIRE_CHARGE)) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.IGNITE, scope, PERMISSION_DEFAULT_IGNITE.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.ignite", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}
