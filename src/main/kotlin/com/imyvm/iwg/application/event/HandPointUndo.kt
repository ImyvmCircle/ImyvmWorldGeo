package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.infra.WorldGeoConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World

fun handPointUndo(
    player: PlayerEntity?,
    world: World?,
    hand: Hand?
): ActionResult {
    if (player == null || world == null || hand == null || world.isClient) {
        return ActionResult.PASS
    }
    if (hand != Hand.MAIN_HAND) return ActionResult.PASS

    val itemStack = player.getStackInHand(hand)
    if (itemStack.item != Items.COMMAND_BLOCK) return ActionResult.PASS

    val playerUUID = player.uuid
    val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: return ActionResult.PASS

    val minPoints = WorldGeoConfig.SELECTION_MIN_POINTS.value
    if (selectedPositions.size <= minPoints) {
        player.sendMessage(
            Translator.tr(
                "interaction.game.point.selection.min_reached",
                minPoints,
                formatPointsList(selectedPositions)
            )
        )
        return ActionResult.SUCCESS
    }

    val removed = selectedPositions.removeAt(selectedPositions.lastIndex)

    player.sendMessage(
        Translator.tr(
            "interaction.game.point.selection.undo",
            "(${removed.x},${removed.y},${removed.z})",
            formatPointsList(selectedPositions)
        )
    )

    return ActionResult.SUCCESS
}
