package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.applyModifiedShape
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
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.rectangle.invalid_rectangle"))
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
    val newGeometry = when (shapeResult) {
        is Result.Ok -> shapeResult.value.typedGeometry as RectangleGeometry
        is Result.Err -> null
    }
    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.RECTANGLE)
    if (changed) {
        val replacementGeometry = requireNotNull(newGeometry)
        player.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.modify.rectangle.success",
            existingScope.scopeName,
            region.name,
            replacementGeometry.west,
            replacementGeometry.north,
            replacementGeometry.east,
            replacementGeometry.south
        ))
    }
    return changed
}
