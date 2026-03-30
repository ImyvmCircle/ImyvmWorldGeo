package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.buildPointUndoMessage
import com.imyvm.iwg.infra.config.SelectionConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level

fun handPointUndo(
    player: Player?,
    world: Level?,
    hand: InteractionHand?
): InteractionResult {
    if (player == null || world == null || hand == null || world.isClientSide) {
        return InteractionResult.PASS
    }
    if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS

    val itemStack = player.getItemInHand(hand)
    if (itemStack.item != Items.NETHER_STAR) return InteractionResult.PASS

    val playerUUID = player.uuid
    val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: return InteractionResult.PASS
    val selectedPositions = selectionState.points

    val minPoints = SelectionConfig.SELECTION_MIN_POINTS.value
    if (selectedPositions.size <= minPoints) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.game.point.selection.min_reached",
                minPoints,
                formatSimpleXZList(selectedPositions)
            )!!
        )
        return InteractionResult.SUCCESS
    }

    val removed = selectedPositions.removeAt(selectedPositions.lastIndex)

    player.sendSystemMessage(buildPointUndoMessage(selectionState, removed))

    return InteractionResult.SUCCESS
}