package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RegionFactory
import com.imyvm.iwg.domain.Result
import com.imyvm.iwg.util.ui.errorMessage
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun onModifyScope(
    player: ServerPlayerEntity,
    targetRegion: Region,
    scopeName: String
): Int {
    val existingScope = targetRegion.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }

    return if (existingScope != null) {
        val playerUUID = player.uuid
        if (!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerUUID)) {
            player.sendMessage(Translator.tr("command.select.not_in_mode"))
            return 0
        }

        val shapeType = existingScope.geoShape?.geoShapeType ?: Region.Companion.GeoShapeType.UNKNOWN
        if (shapeType == Region.Companion.GeoShapeType.UNKNOWN) {
            player.sendMessage(Translator.tr("command.scope.modify.unknown_shape_type"))
            return 0
        }

        val selectedPositions = ImyvmWorldGeo.pointSelectingPlayers[playerUUID] ?: mutableListOf()
        if (shapeType == Region.Companion.GeoShapeType.POLYGON) {
            if (selectedPositions.size < 2) {
                player.sendMessage(Translator.tr("command.scope.modify.polygon_insufficient_points"))
                return 0
            } else if (selectedPositions.size == 2){
                modifyScopePolygonMove(player, targetRegion, existingScope, selectedPositions)
            } else {
                modifyScopePolygonInsertPoint(player, targetRegion, existingScope, selectedPositions)
            }
        } else if (shapeType == Region.Companion.GeoShapeType.CIRCLE) {
            if (selectedPositions.size == 1) {
                modifyScopeCircleRadius(player, targetRegion, existingScope, selectedPositions)
            } else{
                modifyScopeCircleCenter(player, targetRegion, existingScope, selectedPositions)
            }

        } else if (shapeType == Region.Companion.GeoShapeType.RECTANGLE) {
            modifyScopeRectangle(player, targetRegion, existingScope, selectedPositions)
        }
        1
    } else {
        player.sendMessage(Translator.tr("command.scope.scope_not_found", scopeName, targetRegion.name))
        0
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
        player.sendMessage(Translator.tr("command.scope.modify.polygon_duplicate_points"))
        return
    }

    val blockPosList = getPolygonPoints(existingScope)
    if (blockPosList.none { it.x == oldPoint.x && it.z == oldPoint.z }) {
        player.sendMessage(Translator.tr("command.scope.modify.polygon_point_not_found"))
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
        "command.scope.modify.polygon_move_success"
    )
}

fun modifyScopePolygonInsertPoint(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    if (!validatePolygon(player, existingScope)) return

    val pointA = selectedPositions[0]
    val pointB = selectedPositions[1]
    val newPoint = selectedPositions[2]

    val blockPosList = getPolygonPoints(existingScope).toMutableList()
    val indexA = blockPosList.indexOfFirst { it.x == pointA.x && it.z == pointA.z }
    val indexB = blockPosList.indexOfFirst { it.x == pointB.x && it.z == pointB.z }

    if (indexA == -1 || indexB == -1) {
        player.sendMessage(Translator.tr("command.scope.modify.polygon_points_not_found"))
        return
    }

    val n = blockPosList.size
    val areAdjacent = (indexA + 1) % n == indexB || (indexB + 1) % n == indexA
    if (!areAdjacent) {
        player.sendMessage(Translator.tr("command.scope.modify.polygon_points_not_adjacent"))
        return
    }

    val insertIndex = if ((indexA + 1) % n == indexB) indexB else indexA
    blockPosList.add(insertIndex, BlockPos(newPoint.x, newPoint.y, newPoint.z))

    recreateScope(
        player, region, existingScope, blockPosList,
        Region.Companion.GeoShapeType.POLYGON,
        "command.scope.modify.polygon_insert_success"
    )
}

fun modifyScopeCircleRadius(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (shapeParams == null || shapeParams.size < 3) {
        player.sendMessage(Translator.tr("command.scope.modify.circle_radius.invalid_circle"))
        return
    }

    val centerX = shapeParams[0]
    val centerZ = shapeParams[1]
    val oldRadius = shapeParams[2]

    val pos = selectedPositions[0]
    val dx = pos.x - centerX
    val dz = pos.z - centerZ
    val newRadius = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toInt()

    if (newRadius <= 0) {
        player.sendMessage(Translator.tr("command.scope.modify.circle_radius.nonpositive"))
        return
    }

    val newPositions = mutableListOf(
        BlockPos(centerX, 0, centerZ),
        BlockPos(centerX + newRadius, 0, centerZ)
    )

    recreateScope(
        player, region, existingScope, newPositions,
        Region.Companion.GeoShapeType.CIRCLE,
        "command.scope.modify.circle_radius.success",
        oldRadius, newRadius
    )
}

fun modifyScopeCircleCenter(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (shapeParams == null || shapeParams.size < 3) {
        player.sendMessage(Translator.tr("command.scope.modify.circle_center.invalid_circle"))
        return
    }

    val radius = shapeParams[2]
    val oldCenter = selectedPositions[0]
    val newCenter = selectedPositions[1]

    val newPositions = mutableListOf(
        BlockPos(newCenter.x, 0, newCenter.z),
        BlockPos(newCenter.x + radius, 0, newCenter.z)
    )

    recreateScope(
        player, region, existingScope, newPositions,
        Region.Companion.GeoShapeType.CIRCLE,
        "command.scope.modify.circle_center.success",
        "${oldCenter.x},${oldCenter.z}",
        "${newCenter.x},${newCenter.z}",
        radius
    )
}

fun modifyScopeRectangle(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (shapeParams == null || shapeParams.size < 4) {
        player.sendMessage(Translator.tr("command.scope.modify.rectangle.invalid_rectangle"))
        return
    }

    val point = selectedPositions[0]

    var west = shapeParams[0]
    var north = shapeParams[1]
    var east = shapeParams[2]
    var south = shapeParams[3]

    if (kotlin.math.abs(point.x - west) < kotlin.math.abs(point.x - east)) {
        west = point.x
    } else {
        east = point.x
    }

    if (kotlin.math.abs(point.z - north) < kotlin.math.abs(point.z - south)) {
        north = point.z
    } else {
        south = point.z
    }

    val newPositions = mutableListOf(
        BlockPos(west, 0, north),
        BlockPos(east, 0, south)
    )

    recreateScope(
        player, region, existingScope, newPositions,
        Region.Companion.GeoShapeType.RECTANGLE,
        "command.scope.modify.rectangle.success",
        west, north, east, south
    )
}

private fun validatePolygon(player: ServerPlayerEntity, existingScope: Region.Companion.GeoScope): Boolean {
    val shapeParams = existingScope.geoShape?.shapeParameter
    val pointCount = shapeParams?.size

    if (pointCount == null || pointCount < 6 || pointCount % 2 != 0) {
        player.sendMessage(Translator.tr("command.scope.modify.invalid_polygon"))
        return false
    }
    return true
}

private fun getPolygonPoints(existingScope: Region.Companion.GeoScope): List<BlockPos> {
    val shapeParams = existingScope.geoShape?.shapeParameter!!
    val coords = shapeParams.chunked(2)
    return coords.map { pair -> BlockPos(pair[0], 0, pair[1]) }
}

private fun recreateScope(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    newPositions: MutableList<BlockPos>,
    shapeType: Region.Companion.GeoShapeType,
    successMessageKey: String,
    vararg extraArgs: Any
) {
    region.geometryScope.remove(existingScope)

    val newScope = RegionFactory.createScope(
        scopeName = existingScope.scopeName,
        selectedPositions = newPositions,
        shapeType = shapeType
    )

    when (newScope) {
        is Result.Ok -> {
            region.geometryScope.add(newScope.value)
            player.sendMessage(
                Translator.tr(
                    successMessageKey,
                    existingScope.scopeName,
                    region.name,
                    *extraArgs
                )
            )
            ImyvmWorldGeo.pointSelectingPlayers.remove(player.uuid)
        }
        is Result.Err -> {
            region.geometryScope.add(existingScope)
            val errorMsg = errorMessage(newScope.error, shapeType)
            player.sendMessage(errorMsg)
        }
    }
}