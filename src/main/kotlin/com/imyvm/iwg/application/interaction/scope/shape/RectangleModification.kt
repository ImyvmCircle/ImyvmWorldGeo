package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.region.modifyRectangle
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun modifyScopeRectangle(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val geometry = existingScope.geoShape?.typedGeometry as? RectangleGeometry
    if (geometry == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.rectangle.invalid_rectangle")!!)
        return false
    }
    if (selectedPositions.size != 1) {
        val key = if (selectedPositions.isEmpty()) "error.insufficient_points" else "selection.feedback.modify.guidance.rect.excess"
        val message = if (selectedPositions.isEmpty()) Translator.tr(key, "rectangle") else Translator.tr(key)
        player.sendSystemMessage(requireNotNull(message))
        return false
    }

    val point = selectedPositions[0]
    val shapeResult = modifyRectangle(geometry, point)
    if (shapeResult is Result.Err) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.rectangle.invalid_rectangle")!!)
        return false
    }
    val newShape = (shapeResult as Result.Ok).value
    val newGeometry = newShape.typedGeometry as RectangleGeometry

    val changed = recreateScope(
        player, region, existingScope,
        listOf(BlockPos(newGeometry.west, 0, newGeometry.north), BlockPos(newGeometry.east, 0, newGeometry.south)),
        GeoShapeType.RECTANGLE
    )
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.rectangle.success",
            existingScope.scopeName,
            region.name,
            newGeometry.west,
            newGeometry.north,
            newGeometry.east,
            newGeometry.south
        )))
    }
    return changed
}
