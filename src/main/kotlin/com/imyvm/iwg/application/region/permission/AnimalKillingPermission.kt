package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_ANIMAL_KILLING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.mob.Angerable
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.util.ActionResult

fun playerAnimalKillingPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
        if (entity !is AnimalEntity || entity is Angerable) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.ANIMAL_KILLING, scope, PERMISSION_DEFAULT_ANIMAL_KILLING.value)) {
                player.sendMessage(Translator.tr("setting.permission.animal_killing"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}
