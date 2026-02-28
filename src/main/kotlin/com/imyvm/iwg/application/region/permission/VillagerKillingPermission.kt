package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_VILLAGER_KILLING
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.util.ActionResult

fun playerVillagerKillingPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
        if (entity !is VillagerEntity) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            if (!hasPermission(region, player.uuid, PermissionKey.VILLAGER_KILLING, scope, PERMISSION_DEFAULT_VILLAGER_KILLING.value)) {
                player.sendMessage(Translator.tr("setting.permission.villager_killing"))
                return@register ActionResult.FAIL
            }
        }
        ActionResult.PASS
    }
}
