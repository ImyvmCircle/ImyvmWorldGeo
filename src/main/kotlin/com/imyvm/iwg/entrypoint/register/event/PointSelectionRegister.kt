package com.imyvm.iwg.inter.register.event

import com.imyvm.iwg.application.event.handPointSelection
import com.imyvm.iwg.application.event.handPointUndo
import com.imyvm.iwg.application.interaction.clearAllSelections
import com.imyvm.iwg.application.interaction.clearPlayerSelection
import com.imyvm.iwg.application.interaction.onPlayerSelectionWorldChanged
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerPointSelection() {
    UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
        handPointSelection(player, world, hand, hitResult)
    }
    AttackBlockCallback.EVENT.register { player, world, hand, _, _ ->
        handPointUndo(player, world, hand)
    }
    ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
        clearPlayerSelection(handler.player.uuid)
    }
    ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { player, _, _ ->
        onPlayerSelectionWorldChanged(player)
    }
    ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
        clearAllSelections()
    }
}
