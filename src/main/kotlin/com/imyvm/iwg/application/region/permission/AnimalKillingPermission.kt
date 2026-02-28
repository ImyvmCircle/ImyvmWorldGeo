package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_ANIMAL_KILLING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.mob.Angerable
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult

import net.minecraft.util.Hand

fun playerAnimalKillingPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is AnimalEntity || entity is Angerable) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ANIMAL_KILLING, scope, PERMISSION_DEFAULT_ANIMAL_KILLING.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.animal_killing", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (entity !is AnimalEntity || entity is Angerable) return@register true
        val player = source.attacker as? PlayerEntity ?: return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ANIMAL_KILLING, scope, PERMISSION_DEFAULT_ANIMAL_KILLING.value)
            if (denial != null) {
                player.sendMessage(Translator.tr("setting.permission.animal_killing", buildPermissionDenialContext(region, scope, denial)))
                return@register false
            }
        }
        true
    }
}
