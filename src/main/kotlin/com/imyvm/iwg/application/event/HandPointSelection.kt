package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.buildPointAddedMessage
import com.imyvm.iwg.application.selection.formatXZOnly
import com.imyvm.iwg.infra.config.SelectionConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

fun handPointSelection(
    player: Player?,
    world: Level?,
    hand: InteractionHand?,
    hitResult: BlockHitResult?
): InteractionResult {
    if (player == null || world == null || hand == null || hitResult == null || world.isClientSide) {
        return InteractionResult.PASS
    }

    val itemStack = player.getItemInHand(hand)
    if (itemStack.item != Items.NETHER_STAR) return InteractionResult.PASS

    val playerUUID = player.uuid
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: return InteractionResult.PASS
    val selectedPositions = selectionState.points

    val maxPoints = SelectionConfig.SELECTION_MAX_POINTS.value
    if (selectedPositions.size >= maxPoints) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.game.point.selection.max_reached",
                maxPoints,
                formatSimpleXZList(selectedPositions)
            )!!
        )
        return InteractionResult.SUCCESS
    }

    val clickedPos = hitResult.blockPos
    selectedPositions.add(clickedPos)

    player.sendSystemMessage(buildPointAddedMessage(selectionState, clickedPos))

    return InteractionResult.SUCCESS
}

internal fun formatSimpleXZList(positions: List<BlockPos>): String =
    if (positions.isEmpty()) "&7(none)"
    else positions.joinToString(separator = "\n") { "&b(${it.x},${it.z})" }