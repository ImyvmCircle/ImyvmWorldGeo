package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.getPolygonPoints
import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.application.interaction.scope.validatePolygon
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
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
    if (!validatePolygon(player, existingScope)) return false
    val point = selectedPositions.singleOrNull() ?: return invalidPolygonPointCount(player, selectedPositions.size)

    val blockPosList = getPolygonPoints(existingScope)

    return if (blockPosList.any { it.x == point.x && it.z == point.z }) {
        modifyScopePolygonDeletePoint(player, region, existingScope, point)
    } else {
        val (pointA, pointB) = findNearestAdjacentPoints(blockPosList, point)
        val insertPositions = mutableListOf(pointA, pointB, point)
        modifyScopePolygonInsertPoint(player, region, existingScope, insertPositions)
    }
}

fun modifyScopePolygonMove(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    if (!validatePolygon(player, existingScope)) return false
    if (selectedPositions.size != 2) return invalidPolygonPointCount(player, selectedPositions.size)

    val oldPoint = selectedPositions[0]
    val newPoint = selectedPositions[1]

    if (oldPoint.x == newPoint.x && oldPoint.z == newPoint.z) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_duplicate_points")!!)
        return false
    }

    val blockPosList = getPolygonPoints(existingScope)
    if (blockPosList.none { it.x == oldPoint.x && it.z == oldPoint.z }) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_point_not_found")!!)
        return false
    }

    val newPositions = blockPosList.map {
        if (it.x == oldPoint.x && it.z == oldPoint.z) {
            BlockPos(newPoint.x, newPoint.y, newPoint.z)
        } else it
    }.toMutableList()

    return completePolygonModification(
        player, region, existingScope, newPositions,
        "interaction.meta.scope.modify.polygon_move_success"
    )
}

fun modifyScopePolygonInsertPoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    selectedPositions: List<BlockPos>
): Boolean {
    if (!validatePolygon(player, existingScope)) return false
    if (selectedPositions.size != 3) return invalidPolygonPointCount(player, selectedPositions.size)

    val (pointA, pointB, newPoint) = selectedPositions
    val blockPosList = getPolygonPoints(existingScope).toMutableList()

    val indexA = blockPosList.indexOfFirst { it.x == pointA.x && it.z == pointA.z }
    val indexB = blockPosList.indexOfFirst { it.x == pointB.x && it.z == pointB.z }

    if (!validateInsertPoints(player, blockPosList.size, indexA, indexB)) return false

    val insertIndex = if ((indexA + 1) % blockPosList.size == indexB) indexB else indexA
    blockPosList.add(insertIndex, BlockPos(newPoint.x, newPoint.y, newPoint.z))

    return completePolygonModification(
        player, region, existingScope, blockPosList,
        "interaction.meta.scope.modify.polygon_insert_success"
    )
}

fun modifyScopePolygonDeletePoint(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    point: BlockPos
): Boolean {
    val blockPosList = getPolygonPoints(existingScope).toMutableList()
    if (blockPosList.size <= 3) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_minimum_points")!!)
        return false
    }
    if (!blockPosList.removeIf { it.x == point.x && it.z == point.z }) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_point_not_found")!!)
        return false
    }

    return completePolygonModification(
        player, region, existingScope, blockPosList,
        "interaction.meta.scope.modify.polygon_delete_success"
    )
}

private fun completePolygonModification(
    player: ServerPlayer,
    region: Region,
    existingScope: GeoScope,
    positions: List<BlockPos>,
    successKey: String
): Boolean {
    val changed = recreateScope(player, region, existingScope, positions, GeoShapeType.POLYGON)
    if (changed) {
        player.sendSystemMessage(requireNotNull(Translator.tr(successKey, existingScope.scopeName, region.name)))
    }
    return changed
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

private fun validateInsertPoints(player: ServerPlayer, n: Int, indexA: Int, indexB: Int): Boolean {
    if (indexA == -1 || indexB == -1) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_found")!!)
        return false
    }

    val areAdjacent = (indexA + 1) % n == indexB || (indexB + 1) % n == indexA
    if (!areAdjacent) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_adjacent")!!)
        return false
    }
    return true
}
