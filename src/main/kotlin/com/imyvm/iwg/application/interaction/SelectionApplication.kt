package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.buildModifyStartMessage
import com.imyvm.iwg.application.selection.display.clearSelectionDisplay
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

fun onStartSelection(player: ServerPlayer, shapeType: GeoShapeType? = null): Int {
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID] = SelectionState(
            hypotheticalShape = shapeType?.let { HypotheticalShape.Normal(it) }
        )
        if (shapeType != null) {
            player.sendSystemMessage(Translator.tr("interaction.meta.select.start.with_shape", shapeType.name)!!)
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.select.start")!!)
        }
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        0
    }
}

fun onStopSelection(player: ServerPlayer): Int {
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        clearSelectionDisplay(player)
        ImyvmWorldGeo.pointSelectingPlayers.remove(playerUUID)
        player.sendSystemMessage(Translator.tr("interaction.meta.select.stop")!!)
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        0
    }
}

fun onResetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null): Int {
    val playerUUID = player.uuid
    return if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        val state = ImyvmWorldGeo.pointSelectingPlayers[playerUUID]!!
        state.points.clear()
        if (shapeType != null) {
            state.hypotheticalShape = HypotheticalShape.Normal(shapeType)
            player.sendSystemMessage(Translator.tr("interaction.meta.select.reset.with_shape", shapeType.name)!!)
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.select.reset")!!)
        }
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        0
    }
}

fun onSetSelectionShape(player: ServerPlayer, shapeType: GeoShapeType): Int {
    val playerUUID = player.uuid
    if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.not_in_mode")!!)
        return 0
    }
    val state = ImyvmWorldGeo.pointSelectingPlayers[playerUUID]!!
    if (state.hypotheticalShape is HypotheticalShape.ModifyExisting) {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.shape.cannot_change_modify")!!)
        return 0
    }
    state.hypotheticalShape = HypotheticalShape.Normal(shapeType)
    player.sendSystemMessage(Translator.tr("interaction.meta.select.shape.success", shapeType.name)!!)
    return 1
}

fun onStartSelectionForModify(player: ServerPlayer, scope: GeoScope): Int {
    val playerUUID = player.uuid
    return if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
        ImyvmWorldGeo.pointSelectingPlayers[playerUUID] = SelectionState(
            hypotheticalShape = HypotheticalShape.ModifyExisting(scope)
        )
        player.sendSystemMessage(buildModifyStartMessage(scope))
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.select.already")!!)
        0
    }
}