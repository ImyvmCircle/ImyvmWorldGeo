package com.imyvm.iwg.application

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World

fun handPointSelection(
    player: PlayerEntity?,
    world: World?,
    hand: Hand?,
    hitResult: BlockHitResult?
): ActionResult {
    if (player == null || world == null || hand == null || hitResult == null || world.isClient) {
        return ActionResult.PASS
    }

    val itemStack = player.getStackInHand(hand)
    if (itemStack.item != Items.GOLDEN_HOE) return ActionResult.PASS

    val playerUUID = player.uuid
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: return ActionResult.PASS

    val clickedPos = hitResult.blockPos
    selectedPositions.add(clickedPos)

    player.sendMessage(
        Text.literal("Selecting position: $clickedPos, All current selected positions: $selectedPositions")
            .formatted(net.minecraft.util.Formatting.GREEN),
        false
    )

    return ActionResult.SUCCESS
}