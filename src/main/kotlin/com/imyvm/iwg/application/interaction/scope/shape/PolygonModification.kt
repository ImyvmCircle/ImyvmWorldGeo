package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.getPolygonPoints
import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.application.interaction.scope.validatePolygon
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun modifyScopePolygonMonoPoint(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    if (!validatePolygon(player, existingScope)) return

    val point = selectedPositions[0]
    val blockPosList = getPolygonPoints(existingScope)

    if (blockPosList.any { it.x == point.x && it.z == point.z }) {
        modifyScopePolygonDeletePoint(player, region, existingScope, point)
    } else {
        val (pointA, pointB) = findNearestAdjacentPoints(blockPosList, point)
        val insertPositions = mutableListOf(pointA, pointB, point)
        modifyScopePolygonInsertPoint(player, region, existingScope, insertPositions)
    }
}

fun modifyScopePolygonMove(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    if (!validatePolygon(player, existingScope)) return

    val oldPoint = selectedPositions[0]
    val newPoint = selectedPositions[1]

    if (oldPoint == newPoint) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_duplicate_points"))
        return
    }

    val blockPosList = getPolygonPoints(existingScope)
    if (blockPosList.none { it.x == oldPoint.x && it.z == oldPoint.z }) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_point_not_found"))
        return
    }

    val newPositions = blockPosList.map {
        if (it.x == oldPoint.x && it.z == oldPoint.z) {
            BlockPos(newPoint.x, newPoint.y, newPoint.z)
        } else it
    }.toMutableList()

    recreateScope(
        player, region, existingScope, newPositions,
        Region.Companion.GeoShapeType.POLYGON,
        "interaction.meta.scope.modify.polygon_move_success"
    )
}

fun modifyScopePolygonInsertPoint(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    if (!validatePolygon(player, existingScope)) return

    val (pointA, pointB, newPoint) = selectedPositions
    val blockPosList = getPolygonPoints(existingScope).toMutableList()

    val indexA = blockPosList.indexOfFirst { it.x == pointA.x && it.z == pointA.z }
    val indexB = blockPosList.indexOfFirst { it.x == pointB.x && it.z == pointB.z }

    if (!validateInsertPoints(player, blockPosList.size, indexA, indexB)) return

    val insertIndex = if ((indexA + 1) % blockPosList.size == indexB) indexB else indexA
    blockPosList.add(insertIndex, BlockPos(newPoint.x, newPoint.y, newPoint.z))

    recreateScope(
        player, region, existingScope, blockPosList,
        Region.Companion.GeoShapeType.POLYGON,
        "interaction.meta.scope.modify.polygon_insert_success"
    )
}

fun modifyScopePolygonDeletePoint(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    point: BlockPos
) {
    val blockPosList = getPolygonPoints(existingScope).toMutableList()
    if (blockPosList.size <= 3) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_minimum_points"))
        return
    }
    blockPosList.removeIf { it.x == point.x && it.z == point.z }

    recreateScope(
        player, region, existingScope, blockPosList,
        Region.Companion.GeoShapeType.POLYGON,
        "interaction.meta.scope.modify.polygon_delete_success"
    )
}

private fun validateInsertPoints(player: ServerPlayerEntity, n: Int, indexA: Int, indexB: Int): Boolean {
    if (indexA == -1 || indexB == -1) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_found"))
        return false
    }

    val areAdjacent = (indexA + 1) % n == indexB || (indexB + 1) % n == indexA
    if (!areAdjacent) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.polygon_points_not_adjacent"))
        return false
    }
    return true
}