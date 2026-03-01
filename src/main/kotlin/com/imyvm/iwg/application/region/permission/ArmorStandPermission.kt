package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_ARMOR_STAND
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

fun playerArmorStandPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ArmorStandEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (entity !is ArmorStandEntity) return@register true
        val player = source.attacker as? PlayerEntity ?: return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                player.sendMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial)))
                return@register false
            }
        }
        true
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.ARMOR_STAND)) return@register ActionResult.PASS
        val placePos = hitResult.blockPos.offset(hitResult.side)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ArmorStandEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}
