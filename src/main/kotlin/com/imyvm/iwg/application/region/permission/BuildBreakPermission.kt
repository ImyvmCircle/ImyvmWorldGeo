package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BREAK
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUCKET_BUILD
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUCKET_SCOOP
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUILD
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.level.block.DiodeBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.BellBlock
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CakeBlock
import net.minecraft.world.level.block.CartographyTableBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.DaylightDetectorBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.GrindstoneBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LoomBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.SmithingTableBlock
import net.minecraft.world.level.block.StonecutterBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.entity.animal.Bucketable
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Items
import net.minecraft.world.item.SolidBucketItem
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.ClipContext

fun playerBuildPermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (stack.item !is BlockItem || stack.item is SolidBucketItem) return@register InteractionResult.PASS
        val pos = hitResult.blockPos
        val block = world.getBlockState(pos).block
        if (!player.isCrouching && isInteractiveBlock(block)) return@register InteractionResult.PASS
        val cropBlock = (stack.item as BlockItem).block
        if ((cropBlock is CropBlock || cropBlock is SweetBerryBushBlock) && block == Blocks.FARMLAND) return@register InteractionResult.PASS
        val placePos = pos.relative(hitResult.direction)
        if (denyPermissionAt(player, world, placePos, PermissionKey.BUILD, PERMISSION_DEFAULT_BUILD.value,
                "setting.permission.build")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}

fun playerBucketUsePermission() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        val stack = player.getItemInHand(hand)
        if (stack.`is`(Items.BUCKET)) {
            val pos = hitResult.blockPos
            if (denyPermissionAt(player, world, pos, PermissionKey.BUCKET_SCOOP, PERMISSION_DEFAULT_BUCKET_SCOOP.value,
                    "setting.permission.bucket_scoop")) return@register InteractionResult.CONSUME
        } else if (stack.item is SolidBucketItem) {
            val placePos = hitResult.blockPos.relative(hitResult.direction)
            if (denyPermissionAt(player, world, placePos, PermissionKey.BUCKET_BUILD, PERMISSION_DEFAULT_BUCKET_BUILD.value,
                    "setting.permission.bucket_build")) return@register InteractionResult.CONSUME
        }
        InteractionResult.PASS
    }

    UseItemCallback.EVENT.register { player, world, hand ->
        val stack = player.getItemInHand(hand)
        if (stack.item !is BucketItem) return@register InteractionResult.PASS
        val eyePos = player.eyePosition
        val lookVec = player.getViewVector(1.0f)
        val reach = 5.0
        val targetVec = eyePos.add(lookVec.scale(reach))
        val hit = world.clip(
            ClipContext(eyePos, targetVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player)
        )
        if (hit.type != HitResult.Type.BLOCK) return@register InteractionResult.PASS
        val blockHit = hit as BlockHitResult
        val pos = blockHit.blockPos
        if (stack.`is`(Items.BUCKET)) {
            if (denyPermissionAt(player, world, pos, PermissionKey.BUCKET_SCOOP, PERMISSION_DEFAULT_BUCKET_SCOOP.value,
                    "setting.permission.bucket_scoop")) return@register InteractionResult.CONSUME
        } else {
            val placePos = pos.relative(blockHit.direction)
            if (denyPermissionAt(player, world, placePos, PermissionKey.BUCKET_BUILD, PERMISSION_DEFAULT_BUCKET_BUILD.value,
                    "setting.permission.bucket_build")) return@register InteractionResult.CONSUME
        }
        InteractionResult.PASS
    }
}

fun playerBucketScoopEntityPermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (entity !is Bucketable) return@register InteractionResult.PASS
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.BUCKET)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.BUCKET_SCOOP, PERMISSION_DEFAULT_BUCKET_SCOOP.value,
                "setting.permission.bucket_scoop")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}

fun playerBreakPermission() {
    PlayerBlockBreakEvents.BEFORE.register { world, player, pos, blockState, _ ->
        if (isCropOnFarmland(world, pos, blockState.block)) return@register true
        if (denyPermissionAt(player, world, pos, PermissionKey.BREAK, PERMISSION_DEFAULT_BREAK.value,
                "setting.permission.break")) return@register false
        true
    }
}

private fun isInteractiveBlock(block: net.minecraft.world.level.block.Block): Boolean =
    block is BaseEntityBlock ||
    block is DoorBlock ||
    block is TrapDoorBlock ||
    block is FenceGateBlock ||
    block is ButtonBlock ||
    block is LeverBlock ||
    block is NoteBlock ||
    block is DiodeBlock ||
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
