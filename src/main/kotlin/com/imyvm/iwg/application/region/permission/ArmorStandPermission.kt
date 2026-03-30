package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ARMOR_STAND
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

fun playerArmorStandPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ArmorStand) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (entity !is ArmorStand) return@register true
        val player = source.entity as? Player ?: return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.level(), entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                player.sendSystemMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial))!!)
                return@register false
            }
        }
        true
    }

    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.ARMOR_STAND)) return@register InteractionResult.PASS
        val placePos = hitResult.blockPos.relative(hitResult.direction)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    player.sendSystemMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is ArmorStand) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ARMOR_STAND, scope, PERMISSION_DEFAULT_ARMOR_STAND.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.armor_stand", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }
}