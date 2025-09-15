package com.imyvm.iwg.util.command

import com.imyvm.iwg.ImyvmWorldGeo
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World

class UseBlockCommandsHandler: UseBlockCallback {
    override fun interact(player: PlayerEntity?, world: World?, hand: Hand?, hitResult: BlockHitResult?): ActionResult {
        if (player == null || world == null || hand == null || hitResult == null || world.isClient) {
            return ActionResult.PASS
        }

        val itemStack = player.getStackInHand(hand)
        if (itemStack.item == Items.GOLDEN_HOE) {
            val playerUUID = player.uuid
            if (ImyvmWorldGeo.commandlySelectingPlayers.containsKey(playerUUID)) {
                val clickedPos = hitResult.blockPos
                ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]?.add(clickedPos)

                ImyvmWorldGeo.logger.info("Player $playerUUID selected position $clickedPos,\n all their current selected positions: ${ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]}")
                player.sendMessage(
                    Text.literal("Selecting position: $clickedPos, All current selected position: ${ImyvmWorldGeo.commandlySelectingPlayers[playerUUID]}")
                        .formatted(net.minecraft.util.Formatting.GREEN),
                    false
                )
                return ActionResult.SUCCESS
            }
        }
        return ActionResult.PASS
    }
}