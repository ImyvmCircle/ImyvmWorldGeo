package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_PVP
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult

import net.minecraft.util.Hand

fun playerPvpPermission() {
    AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is PlayerEntity) return@register ActionResult.PASS
        val target = entity as PlayerEntity
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, target.blockX, target.blockZ)
        regionAndScope?.let { (region, scope) ->
            val attackerDenial = getPermissionDenialSource(region, player.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            val defenderDenial = getPermissionDenialSource(region, target.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            if (attackerDenial != null || defenderDenial != null) {
                if (hitResult == null) {
                    val ctx = buildPermissionDenialContext(region, scope, attackerDenial ?: defenderDenial!!)
                    val messageKey = when {
                        attackerDenial != null && defenderDenial != null -> "setting.permission.pvp.both"
                        attackerDenial != null -> "setting.permission.pvp.attacker_only"
                        else -> "setting.permission.pvp.defender_only"
                    }
                    player.sendMessage(Translator.tr(messageKey, ctx))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }

    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        if (entity !is PlayerEntity) return@register true
        val attacker = source.attacker as? PlayerEntity ?: return@register true
        val target = entity as PlayerEntity
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(entity.world, target.blockX, target.blockZ)
        regionAndScope?.let { (region, scope) ->
            val attackerDenial = getPermissionDenialSource(region, attacker.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            val defenderDenial = getPermissionDenialSource(region, target.uuid, PermissionKey.PVP, scope, PERMISSION_DEFAULT_PVP.value)
            if (attackerDenial != null || defenderDenial != null) {
                val ctx = buildPermissionDenialContext(region, scope, attackerDenial ?: defenderDenial!!)
                val messageKey = when {
                    attackerDenial != null && defenderDenial != null -> "setting.permission.pvp.both"
                    attackerDenial != null -> "setting.permission.pvp.attacker_only"
                    else -> "setting.permission.pvp.defender_only"
                }
                attacker.sendMessage(Translator.tr(messageKey, ctx))
                return@register false
            }
        }
        true
    }
}
