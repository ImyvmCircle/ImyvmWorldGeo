package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.helper.errorMessage
import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import kotlin.math.hypot

fun modifyScopeCircleRadius(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val circle = circleGeometry(player, existingScope, "circle_radius.invalid_circle") ?: return false
    val point = selectedPositions.singleOrNull() ?: return invalidPointCount(player, selectedPositions.size)
    val centerX = circle.centerX
    val centerZ = circle.centerZ
    val oldRadius = circle.radius
    val radius = hypot(point.x.toDouble() - centerX, point.z.toDouble() - centerZ)
    if (radius > Int.MAX_VALUE) return coordinateRangeExceeded(player)
    val newRadius = radius.toInt()
    if (newRadius <= 0) {
        player.sendSystemMessage(requireNotNull(Translator.tr("interaction.meta.scope.modify.circle_radius.non_positive")))
        return false
    }

    val changed = recreateScope(
        player,
        region,
        existingScope,
        listOf(BlockPos(centerX, 0, centerZ), point),
        GeoShapeType.CIRCLE
    )
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.circle_radius.success",
            existingScope.scopeName,
            region.name,
            oldRadius,
            newRadius
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

    val radius = circle.radius
    val newCenter = selectedPositions[1]
    val edgeX = newCenter.x.toLong() + radius
    if (edgeX !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return coordinateRangeExceeded(player)

    val changed = recreateScope(
        player,
        region,
        existingScope,
        listOf(newCenter, BlockPos(edgeX.toInt(), 0, newCenter.z)),
        GeoShapeType.CIRCLE
    )
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.circle_center.success",
            existingScope.scopeName,
            region.name,
            "$centerX,$centerZ",
            "${newCenter.x},${newCenter.z}",
            radius
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

private fun coordinateRangeExceeded(player: ServerPlayer): Boolean {
    errorMessage(CreationError.CoordinateRangeExceeded, GeoShapeType.CIRCLE)
        .forEach(player::sendSystemMessage)
    return false
}
