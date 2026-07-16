package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.applyModifiedShape
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.application.region.modifyCircleCenter
import com.imyvm.iwg.application.region.modifyCircleRadius
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

fun modifyScopeCircleRadius(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val circle = circleGeometry(player, existingScope, "circle_radius.invalid_circle") ?: return false
    val point = selectedPositions.singleOrNull() ?: return invalidPointCount(player, selectedPositions.size)
    val oldRadius = circle.radius
    if (point.x == circle.centerX && point.z == circle.centerZ) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.scope.modify.circle_radius.non_positive")))
        return false
    }

    val shapeResult = modifyCircleRadius(circle, point)
    val newRadius = when (shapeResult) {
        is Result.Ok -> (shapeResult.value.typedGeometry as CircleGeometry).radius
        is Result.Err -> null
    }
    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.CIRCLE)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.circle_radius.success",
            existingScope.scopeName,
            region.name,
            oldRadius,
            requireNotNull(newRadius)
        )))
    }
    return changed
}

fun modifyScopeCircleCenter(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val circle = circleGeometry(player, existingScope, "circle_center.invalid_circle") ?: return false
    if (selectedPositions.size != 2) return invalidPointCount(player, selectedPositions.size)
    val oldCenter = selectedPositions[0]
    val centerX = circle.centerX
    val centerZ = circle.centerZ
    if (oldCenter.x != centerX || oldCenter.z != centerZ) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "selection.feedback.modify.guidance.circle.2points_invalid_pt1",
            oldCenter.x,
            oldCenter.z,
            centerX,
            centerZ
        )))
        return false
    }

    val newCenter = selectedPositions[1]
    val shapeResult = modifyCircleCenter(circle, newCenter)
    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.CIRCLE)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.circle_center.success",
            existingScope.scopeName,
            region.name,
            "$centerX,$centerZ",
            "${newCenter.x},${newCenter.z}",
            circle.radius
        )))
    }
    return changed
}

private fun circleGeometry(player: ServerPlayer, scope: GeoScope, messageKey: String): CircleGeometry? {
    val geometry = scope.geoShape?.typedGeometry as? CircleGeometry
    if (geometry == null) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.scope.modify.$messageKey")))
    }
    return geometry
}

private fun invalidPointCount(player: ServerPlayer, count: Int): Boolean {
    val key = if (count == 0) "error.insufficient_points" else "selection.feedback.modify.guidance.circle.excess"
    val message = if (count == 0) Translator.tr(key, "circle") else Translator.tr(key)
    player.sendSystemMessage(requireNotNull(message))
    return false
}
