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
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

fun playerItemFramePermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrame) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.ITEM_FRAME) && !stack.`is`(Items.GLOW_ITEM_FRAME)) return@register InteractionResult.PASS
        val pos = hitResult.blockPos
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ItemFrame) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ITEM_FRAME, scope, PERMISSION_DEFAULT_ITEM_FRAME.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.item_frame", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}