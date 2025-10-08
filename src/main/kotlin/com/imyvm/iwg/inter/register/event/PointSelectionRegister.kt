package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.handPointSelection
import net.fabricmc.fabric.api.event.player.UseBlockCallback

fun registerPointSelection() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        handPointSelection(player, world, hand, hitResult)
    }
}


