package com.imyvm.iwg.useblock

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
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
            val clickedPos = hitResult.blockPos
            TODO("Handle golden hoe interaction at $clickedPos by player $playerUUID")
        }

        return ActionResult.PASS
    }

}