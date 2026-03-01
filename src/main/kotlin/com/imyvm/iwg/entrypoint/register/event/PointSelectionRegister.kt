package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.handPointSelection
import com.imyvm.iwg.application.event.handPointUndo
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback

fun registerPointSelection() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        handPointSelection(player, world, hand, hitResult)
    }
    AttackBlockCallback.EVENT.register { player, world, hand, _, _ ->
        handPointUndo(player, world, hand)
    }
}
