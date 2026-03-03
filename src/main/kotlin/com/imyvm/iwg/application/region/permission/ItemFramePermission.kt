package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ITEM_FRAME
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

fun playerItemFramePermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrameEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.ITEM_FRAME) && !stack.isOf(Items.GLOW_ITEM_FRAME)) return@register ActionResult.PASS
        val pos = hitResult.blockPos
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrameEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}
