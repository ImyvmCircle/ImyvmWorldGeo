package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_VILLAGER_KILLING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionResult

import net.minecraft.world.InteractionHand

fun playerVillagerKillingPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is Villager) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.VILLAGER_KILLING, scope, PERMISSION_DEFAULT_VILLAGER_KILLING.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.villager_killing", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (entity !is Villager) return@register true
        val player = source.entity as? Player ?: return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.level(), entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.VILLAGER_KILLING, scope, PERMISSION_DEFAULT_VILLAGER_KILLING.value)
            if (denial != null) {
                player.sendSystemMessage(Translator.tr("setting.permission.villager_killing", buildPermissionDenialContext(region, scope, denial))!!)
                return@register false
            }
        }
        true
    }
}