package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.application.region.permission.helper.buildPermissionDenialContext
import com.imyvm.iwg.application.region.permission.helper.getPermissionDenialSource
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_BREAK
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_BUCKET_BUILD
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_BUCKET_SCOOP
import com.imyvm.iwg.infra.WorldGeoConfig.Companion.PERMISSION_DEFAULT_BUILD
import com.imyvm.iwg.util.text.Translator
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.block.AbstractRedstoneGateBlock
import net.minecraft.block.BedBlock
import net.minecraft.block.BellBlock
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.Blocks
import net.minecraft.block.ButtonBlock
import net.minecraft.block.CakeBlock
import net.minecraft.block.CartographyTableBlock
import net.minecraft.block.CraftingTableBlock
import net.minecraft.block.CropBlock
import net.minecraft.block.DaylightDetectorBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.GrindstoneBlock
import net.minecraft.block.LeverBlock
import net.minecraft.block.LoomBlock
import net.minecraft.block.NoteBlock
import net.minecraft.block.SmithingTableBlock
import net.minecraft.block.StonecutterBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.entity.Bucketable
import net.minecraft.item.BlockItem
import net.minecraft.item.BucketItem
import net.minecraft.item.Items
import net.minecraft.item.PowderSnowBucketItem
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext

fun playerBuildPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (stack.item !is BlockItem || stack.item is PowderSnowBucketItem) return@register ActionResult.PASS
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (!player.isSneaking && isInteractiveBlock(block)) return@register ActionResult.PASS
        val cropBlock = (stack.item as BlockItem).block
        if ((cropBlock is CropBlock || cropBlock is SweetBerryBushBlock) && block == Blocks.FARMLAND) return@register ActionResult.PASS
        val placePos = pos.offset(hitResult.side)
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUILD, scope, PERMISSION_DEFAULT_BUILD.value)
            if (denial != null) {
                if (hand == Hand.MAIN_HAND) {
                    player.sendMessage(Translator.tr("setting.permission.build", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}

fun playerBucketUsePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getStackInHand(hand)
        if (stack.isOf(Items.BUCKET)) {
            val pos = hitResult.blockPos
            val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
            regionAndScope?.let { (region, scope) ->
                val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUCKET_SCOOP, scope, PERMISSION_DEFAULT_BUCKET_SCOOP.value)
                if (denial != null) {
                    if (hand == Hand.MAIN_HAND) {
                        player.sendMessage(Translator.tr("setting.permission.bucket_scoop", buildPermissionDenialContext(region, scope, denial)))
                    }
                    return@register ActionResult.CONSUME
                }
            }
        } else if (stack.item is PowderSnowBucketItem) {
            val placePos = hitResult.blockPos.offset(hitResult.side)
            val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
            regionAndScope?.let { (region, scope) ->
                val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUCKET_BUILD, scope, PERMISSION_DEFAULT_BUCKET_BUILD.value)
                if (denial != null) {
                    if (hand == Hand.MAIN_HAND) {
                        player.sendMessage(Translator.tr("setting.permission.bucket_build", buildPermissionDenialContext(region, scope, denial)))
                    }
                    return@register ActionResult.CONSUME
                }
            }
        }
        ActionResult.PASS
    }

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
        val blockHit = hit as BlockHitResult
        val pos = blockHit.blockPos
        if (stack.isOf(Items.BUCKET)) {
            val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z)
            regionAndScope?.let { (region, scope) ->
                val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUCKET_SCOOP, scope, PERMISSION_DEFAULT_BUCKET_SCOOP.value)
                if (denial != null) {
                    if (hand == Hand.MAIN_HAND) {
                        player.sendMessage(Translator.tr("setting.permission.bucket_scoop", buildPermissionDenialContext(region, scope, denial)))
                    }
                    return@register TypedActionResult.consume(stack)
                }
            }
        } else {
            val placePos = pos.offset(blockHit.side)
            val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, placePos.x, placePos.z)
            regionAndScope?.let { (region, scope) ->
                val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUCKET_BUILD, scope, PERMISSION_DEFAULT_BUCKET_BUILD.value)
                if (denial != null) {
                    if (hand == Hand.MAIN_HAND) {
                        player.sendMessage(Translator.tr("setting.permission.bucket_build", buildPermissionDenialContext(region, scope, denial)))
                    }
                    return@register TypedActionResult.consume(stack)
                }
            }
        }
        TypedActionResult.pass(stack)
    }
}

fun playerBucketScoopEntityPermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is Bucketable) return@register ActionResult.PASS
        val stack = player.getStackInHand(hand)
        if (!stack.isOf(Items.BUCKET)) return@register ActionResult.PASS
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(world, entity.blockX, entity.blockZ)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BUCKET_SCOOP, scope, PERMISSION_DEFAULT_BUCKET_SCOOP.value)
            if (denial != null) {
                if (hitResult == null) {
                    player.sendMessage(Translator.tr("setting.permission.bucket_scoop", buildPermissionDenialContext(region, scope, denial)))
                }
                return@register ActionResult.CONSUME
            }
        }
        ActionResult.PASS
    }
}

fun playerBreakPermission() {
    PlayerBlockBreakEvents.BEFORE.register { world, player, pos, blockState, _ ->
        if (isCropOnFarmland(world, pos, blockState.block)) return@register true
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, pos.x, pos.z)
        regionAndScope?.let { (region, scope) ->
            val denial = getPermissionDenialSource(region, player.uuid, PermissionKey.BREAK, scope, PERMISSION_DEFAULT_BREAK.value)
            if (denial != null) {
                player.sendMessage(Translator.tr("setting.permission.break", buildPermissionDenialContext(region, scope, denial)))
                return@register false
            }
        }
        true
    }
}

private fun isInteractiveBlock(block: net.minecraft.block.Block): Boolean =
    block is BlockWithEntity ||
    block is DoorBlock ||
    block is TrapdoorBlock ||
    block is FenceGateBlock ||
    block is ButtonBlock ||
    block is LeverBlock ||
    block is NoteBlock ||
    block is AbstractRedstoneGateBlock ||
    block is DaylightDetectorBlock ||
    block is BellBlock ||
    block is CraftingTableBlock ||
    block is GrindstoneBlock ||
    block is LoomBlock ||
    block is StonecutterBlock ||
    block is CartographyTableBlock ||
    block is SmithingTableBlock ||
    block is BedBlock ||
    block is CakeBlock