package com.imyvm.iwg.application

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.ui.text.Translator
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
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

    player.sendMessage(Translator.tr(
        "interaction.point.selection", clickedPos, selectedPositions.joinToString(separator = "\n") { it.toString() })
    )

    return ActionResult.SUCCESS
}
