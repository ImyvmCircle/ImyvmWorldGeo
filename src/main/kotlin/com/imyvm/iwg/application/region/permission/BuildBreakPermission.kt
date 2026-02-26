package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.hasPermission
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_BUILD_BREAK
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BucketItem
import net.minecraft.util.ActionResult
import net.minecraft.util.TypedActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext

fun playerBuildPermission(){
    UseBlockCallback.EVENT.register { player, _, _, hitResult ->
        val pos = hitResult.blockPos
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register ActionResult.FAIL
        }
        val placePos = pos.offset(hitResult.side)
        if (!playerCanBuildOrBreak(player, placePos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register ActionResult.FAIL
        }
        ActionResult.PASS
    }
}

fun playerBucketUsePermission() {
    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getStackInHand(hand)
        if (stack.item !is BucketItem) return@register TypedActionResult.pass(stack)
        val eyePos = player.eyePos
        val lookVec = player.getRotationVec(1.0f)
        val reach = 5.0
        val targetVec = eyePos.add(lookVec.multiply(reach))
        val hit = world.raycast(
            RaycastContext(eyePos, targetVec, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, player)
        )
        if (hit.type != HitResult.Type.BLOCK) return@register TypedActionResult.pass(stack)
        val pos = (hit as BlockHitResult).blockPos
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.build"))
            return@register TypedActionResult.fail(stack)
        }
        TypedActionResult.pass(stack)
    }
}

fun playerBreakPermission(){
    PlayerBlockBreakEvents.BEFORE.register { _, player, pos, _, _ ->
        if (!playerCanBuildOrBreak(player, pos)) {
            player.sendMessage(Translator.tr("setting.permission.break"))
            return@register false
        }
        true
    }
}

private fun playerCanBuildOrBreak(player: PlayerEntity, pos: BlockPos): Boolean {
    val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, pos.x, pos.z)
    regionAndScope?.let { (region, scope) ->
        return hasPermission(region, player.uuid, PermissionKey.BUILD_BREAK, scope, PERMISSION_DEFAULT_BUILD_BREAK.value)
    }
    return true
}