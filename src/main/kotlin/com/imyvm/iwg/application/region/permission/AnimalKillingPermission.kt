package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ANIMAL_KILLING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.dolphin.Dolphin
import net.minecraft.world.entity.animal.fish.AbstractFish
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.entity.animal.golem.SnowGolem
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionResult

import net.minecraft.world.InteractionHand

private fun isProtectedAnimal(entity: Entity): Boolean {
    return when {
        entity is Goat -> false
        entity is Animal && entity !is NeutralMob -> true
        entity is AbstractFish -> true
        entity is Squid -> true
        entity is Dolphin -> true
        entity is Allay -> true
        entity is SnowGolem -> true
        else -> false
    }
}

fun playerAnimalKillingPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (!isProtectedAnimal(entity)) return@register InteractionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ANIMAL_KILLING, scope, PERMISSION_DEFAULT_ANIMAL_KILLING.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendSystemMessage(Translator.tr("setting.permission.animal_killing", buildPermissionDenialContext(region, scope, denial))!!)
                }
                return@register InteractionResult.CONSUME
            }
        }
        InteractionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (!isProtectedAnimal(entity)) return@register true
        val player = source.entity as? Player ?: return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.level(), entity.blockPosition().x, entity.blockPosition().z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.ANIMAL_KILLING, scope, PERMISSION_DEFAULT_ANIMAL_KILLING.value)
            if (denial != null) {
                player.sendSystemMessage(Translator.tr("setting.permission.animal_killing", buildPermissionDenialContext(region, scope, denial))!!)
                return@register false
            }
        }
        true
    }
}