package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.buildPointAddedMessage
import com.imyvm.iwg.application.selection.formatXZOnly
import com.imyvm.iwg.infra.config.SelectionConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
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
    if (itemStack.item != Items.NETHER_STAR) return ActionResult.PASS

    val playerUUID = player.uuid
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: return ActionResult.PASS
    val selectedPositions = selectionState.points

    val maxPoints = SelectionConfig.SELECTION_MAX_POINTS.value
    if (selectedPositions.size >= maxPoints) {
        player.sendMessage(
            Translator.tr(
                "interaction.game.point.selection.max_reached",
                maxPoints,
                formatSimpleXZList(selectedPositions)
            )
        )
        return ActionResult.SUCCESS
    }

    val clickedPos = hitResult.blockPos
    selectedPositions.add(clickedPos)

    player.sendMessage(buildPointAddedMessage(selectionState, clickedPos))

    return ActionResult.SUCCESS
}

internal fun formatSimpleXZList(positions: List<BlockPos>): String =
    if (positions.isEmpty()) "&7(none)"
    else positions.joinToString(separator = "\n") { "&b(${it.x},${it.z})" }
