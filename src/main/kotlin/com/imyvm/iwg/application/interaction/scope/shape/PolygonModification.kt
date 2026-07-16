package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.applyModifiedShape
import com.imyvm.iwg.application.region.modifyPolygonDelete
import com.imyvm.iwg.application.region.modifyPolygonInsert
import com.imyvm.iwg.application.region.modifyPolygonMove
import com.imyvm.iwg.application.region.PolygonModificationResult
import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos

fun modifyScopePolygonMonoPoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val geometry = polygonGeometry(player, existingScope) ?: return false
    val point = selectedPositions.singleOrNull() ?: return invalidPolygonPointCount(player, selectedPositions.size)

    val blockPosList = List(geometry.vertexCount) { BlockPos(geometry.x(it), 0, geometry.z(it)) }

    return if (blockPosList.any { it.x == point.x && it.z == point.z }) {
        modifyScopePolygonDeletePoint(player, region, existingScope, geometry, point)
    } else {
        val (pointA, pointB) = findNearestAdjacentPoints(blockPosList, point)
        modifyScopePolygonInsertPoint(player, region, existingScope, geometry, listOf(pointA, pointB, point))
    }
}

fun modifyScopePolygonMove(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val geometry = polygonGeometry(player, existingScope) ?: return false
    if (selectedPositions.size != 2) return invalidPolygonPointCount(player, selectedPositions.size)

    val oldPoint = selectedPositions[0]
    val newPoint = selectedPositions[1]

    val shapeResult = when (val modification = modifyPolygonMove(geometry, oldPoint, newPoint)) {
        is PolygonModificationResult.Shape -> {
            if (modification.result == Result.Err(CreationError.DuplicatedPoints)) {
                player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_duplicate_points")!!)
                return false
            }
            modification.result
        }
        PolygonModificationResult.PointNotFound -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_point_not_found")!!)
            return false
        }
        PolygonModificationResult.MinimumPoints,
        PolygonModificationResult.PointsNotAdjacent -> error("unexpected polygon move result")
    }

    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.POLYGON)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.polygon_move_success", existingScope.scopeName, region.name
        )))
    }
    return changed
}

fun modifyScopePolygonInsertPoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    val geometry = polygonGeometry(player, existingScope) ?: return false
    if (selectedPositions.size != 3) return invalidPolygonPointCount(player, selectedPositions.size)
    return modifyScopePolygonInsertPoint(player, region, existingScope, geometry, selectedPositions)
}

private fun modifyScopePolygonInsertPoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    geometry: PolygonGeometry,
    selectedPositions: List<BlockPos>
): Boolean {
    val (pointA, pointB, newPoint) = selectedPositions
    val shapeResult = when (val modification = modifyPolygonInsert(geometry, pointA, pointB, newPoint)) {
        is PolygonModificationResult.Shape -> modification.result
        PolygonModificationResult.PointNotFound -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_found")!!)
            return false
        }
        PolygonModificationResult.PointsNotAdjacent -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_adjacent")!!)
            return false
        }
        PolygonModificationResult.MinimumPoints -> error("unexpected polygon insert result")
    }

    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.POLYGON)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.polygon_insert_success", existingScope.scopeName, region.name
        )))
    }
    return changed
}

private fun modifyScopePolygonDeletePoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    geometry: PolygonGeometry,
    point: BlockPos
): Boolean {
    val shapeResult = when (val modification = modifyPolygonDelete(geometry, point)) {
        is PolygonModificationResult.Shape -> modification.result
        PolygonModificationResult.MinimumPoints -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_minimum_points")!!)
            return false
        }
        PolygonModificationResult.PointNotFound -> {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_point_not_found")!!)
            return false
        }
        PolygonModificationResult.PointsNotAdjacent -> error("unexpected polygon delete result")
    }

    val changed = applyModifiedShape(player, region, existingScope, shapeResult, GeoShapeType.POLYGON)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(
            "interaction.meta.scope.modify.polygon_delete_success", existingScope.scopeName, region.name
        )))
    }
    return changed
}

private fun polygonGeometry(player: ServerPlayer, scope: GeoScope): PolygonGeometry? {
    val geometry = scope.geoShape?.typedGeometry as? PolygonGeometry
    if (geometry == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.invalid_polygon")!!)
    }
    return geometry
}

private fun invalidPolygonPointCount(player: ServerPlayer, count: Int): Boolean {
    val key = if (count == 0) {
        "error.insufficient_points"
    } else {
        "selection.feedback.modify.guidance.polygon.excess"
    }
    val message = if (count == 0) Translator.tr(key, "polygon") else Translator.tr(key)
    player.sendSystemMessage(requireNotNull(message))
    return false
}
