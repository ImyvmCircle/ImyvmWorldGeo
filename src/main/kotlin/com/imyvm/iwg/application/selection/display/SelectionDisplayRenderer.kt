package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.getEffectiveShapeType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.isConvex
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun displaySelectionForAllPlayers(server: MinecraftServer) {
    server.playerManager.playerList.forEach { player ->
        val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid] ?: return@forEach
        displayForPlayer(player, state)
    }
}

fun displayScopeBoundariesForActionBarPlayers(server: MinecraftServer) {
    server.playerManager.playerList
        .filter { it.uuid in ImyvmWorldGeo.locationActionBarEnabledPlayers && !ImyvmWorldGeo.pointSelectingPlayers.containsKey(it.uuid) }
        .forEach { player ->
            val playerWorld = player.serverWorld
            val scopes = RegionDatabase.getRegionList()
                .flatMap { it.geometryScope }
                .filter { it.geoShape != null && it.getWorld(server) == playerWorld }
            displayScopeBoundariesForPlayer(player, scopes)
        }
}

private fun displayForPlayer(player: ServerPlayerEntity, state: SelectionState) {
    val points = state.points

    beginPillarTracking()
    points.forEach { emitPillar(player, it.x, it.z) }

    if (points.isEmpty()) {
        val shape = state.hypotheticalShape
        if (shape is HypotheticalShape.ModifyExisting) {
            displayOriginalScope(player, shape.scope)
        }
        commitPillars(player)
        return
    }

    when (val shape = state.hypotheticalShape) {
        is HypotheticalShape.ModifyExisting -> displayForModifyExisting(player, points, shape.scope)
        is HypotheticalShape.Normal -> displayForShape(player, points, shape.shapeType)
        null -> displayForShape(player, points, state.getEffectiveShapeType())
    }

    commitPillars(player)
}

private fun displayForShape(player: ServerPlayerEntity, points: List<BlockPos>, shapeType: GeoShapeType) {
    when (shapeType) {
        GeoShapeType.CIRCLE -> displayCircleSelection(player, points)
        GeoShapeType.RECTANGLE -> displayRectangleSelection(player, points)
        GeoShapeType.POLYGON -> displayPolygonSelection(player, points)
        else -> {}
    }
}

private fun displayCircleSelection(player: ServerPlayerEntity, points: List<BlockPos>) {
    if (points.size < 2) return
    val params = evaluateCircleShape(points[0], points[1]) ?: return
    emitLineSurface(player, points[0], points[1])
    drawCircleOutline(player, params[0], params[1], params[2])
}

private fun displayRectangleSelection(player: ServerPlayerEntity, points: List<BlockPos>) {
    if (points.size < 2) return
    val params = evaluateRectangleShape(points[0], points[1]) ?: return
    val west = params[0]; val north = params[1]; val east = params[2]; val south = params[3]
    val corners = listOf(BlockPos(west, 0, north), BlockPos(east, 0, north), BlockPos(east, 0, south), BlockPos(west, 0, south))
    corners.forEach { emitPillar(player, it.x, it.z) }
    emitLineSurface(player, corners[0], corners[1])
    emitLineSurface(player, corners[1], corners[2])
    emitLineSurface(player, corners[2], corners[3])
    emitLineSurface(player, corners[3], corners[0])
}

private fun displayPolygonSelection(player: ServerPlayerEntity, points: List<BlockPos>) {
    if (points.size < 3) return
    if (!isConvex(points)) return
    if (checkPolygonSize(points) != null) return
    for (i in points.indices) {
        emitLineSurface(player, points[i], points[(i + 1) % points.size])
    }
}

private fun displayForModifyExisting(player: ServerPlayerEntity, newPoints: List<BlockPos>, scope: GeoScope) {
    displayOriginalScope(player, scope)
    val shapeType = scope.geoShape?.geoShapeType ?: return
    val existingParams = scope.geoShape?.shapeParameter ?: return

    when (shapeType) {
        GeoShapeType.RECTANGLE -> displayModifyRectanglePreview(player, newPoints, existingParams)
        GeoShapeType.CIRCLE -> displayModifyCirclePreview(player, newPoints, existingParams)
        GeoShapeType.POLYGON -> displayModifyPolygonPreview(player, newPoints, existingParams)
        else -> {}
    }
}

fun displayScopeBoundaryForPlayer(player: ServerPlayerEntity, scope: GeoScope) {
    beginPillarTracking()
    displayOriginalScope(player, scope)
    commitPillars(player)
}

fun displayScopeBoundariesForPlayer(player: ServerPlayerEntity, scopes: List<GeoScope>) {
    if (scopes.isEmpty()) return
    beginPillarTracking()
    scopes.forEach { displayOriginalScope(player, it) }
    commitPillars(player)
}

fun displayOriginalScope(player: ServerPlayerEntity, scope: GeoScope) {
    val geoShape = scope.geoShape ?: return
    val params = geoShape.shapeParameter
    when (geoShape.geoShapeType) {
        GeoShapeType.RECTANGLE -> {
            val west = params[0]; val north = params[1]; val east = params[2]; val south = params[3]
            val corners = listOf(BlockPos(west, 0, north), BlockPos(east, 0, north), BlockPos(east, 0, south), BlockPos(west, 0, south))
            corners.forEach { emitPillar(player, it.x, it.z) }
            emitLineSurfaceOld(player, corners[0], corners[1])
            emitLineSurfaceOld(player, corners[1], corners[2])
            emitLineSurfaceOld(player, corners[2], corners[3])
            emitLineSurfaceOld(player, corners[3], corners[0])
        }
        GeoShapeType.CIRCLE -> {
            val centerX = params[0]; val centerZ = params[1]; val radius = params[2]
            emitPillar(player, centerX, centerZ)
            emitPillar(player, centerX, centerZ + radius)
            drawCircleOutlineOld(player, centerX, centerZ, radius)
        }
        GeoShapeType.POLYGON -> {
            val vertices = params.chunked(2).map { BlockPos(it[0], 0, it[1]) }
            vertices.forEach { emitPillar(player, it.x, it.z) }
            for (i in vertices.indices) {
                emitLineSurfaceOld(player, vertices[i], vertices[(i + 1) % vertices.size])
            }
        }
        else -> {}
    }
}

private fun displayModifyRectanglePreview(player: ServerPlayerEntity, newPoints: List<BlockPos>, existingParams: List<Int>) {
    if (newPoints.size != 1) return
    val newParams = evaluateModifyRectangle(newPoints[0], existingParams) ?: return
    val west = newParams[0]; val north = newParams[1]; val east = newParams[2]; val south = newParams[3]
    val corners = listOf(BlockPos(west, 0, north), BlockPos(east, 0, north), BlockPos(east, 0, south), BlockPos(west, 0, south))
    corners.forEach { emitPillar(player, it.x, it.z) }
    emitLineSurface(player, corners[0], corners[1])
    emitLineSurface(player, corners[1], corners[2])
    emitLineSurface(player, corners[2], corners[3])
    emitLineSurface(player, corners[3], corners[0])
}

private fun displayModifyCirclePreview(player: ServerPlayerEntity, newPoints: List<BlockPos>, existingParams: List<Int>) {
    val centerX = existingParams[0]; val centerZ = existingParams[1]
    when (newPoints.size) {
        1 -> {
            val pt = newPoints[0]
            if (pt.x == centerX && pt.z == centerZ) return
            val newParams = evaluateModifyCircleRadius(pt, existingParams) ?: return
            drawCircleOutline(player, newParams[0], newParams[1], newParams[2])
        }
        2 -> {
            val ptA = newPoints[0]; val ptB = newPoints[1]
            val (centerPt, edgePt) = if (ptA.x == centerX && ptA.z == centerZ) (ptA to ptB)
                                      else if (ptB.x == centerX && ptB.z == centerZ) (ptB to ptA)
                                      else return
            val newParams = evaluateModifyCircleCenter(centerPt, edgePt, existingParams) ?: return
            drawCircleOutline(player, newParams[0], newParams[1], newParams[2])
            emitLineSurface(player, centerPt, edgePt)
        }
        else -> {}
    }
}

private fun displayModifyPolygonPreview(player: ServerPlayerEntity, newPoints: List<BlockPos>, existingParams: List<Int>) {
    val existingVertices = existingParams.chunked(2).map { BlockPos(it[0], 0, it[1]) }
    when (newPoints.size) {
        1 -> {
            val pt = newPoints[0]
            if (existingVertices.any { it.x == pt.x && it.z == pt.z }) return
            val newPolygon = evaluateModifyPolygonInsert(pt, existingVertices) ?: return
            val idx = newPolygon.indexOfFirst { it.x == pt.x && it.z == pt.z }
            if (idx == -1) return
            val prev = newPolygon[(idx - 1 + newPolygon.size) % newPolygon.size]
            val next = newPolygon[(idx + 1) % newPolygon.size]
            emitLineSurface(player, prev, pt)
            emitLineSurface(player, pt, next)
        }
        2 -> {
            val ptA = newPoints[0]; val ptB = newPoints[1]
            val oldVertex = if (existingVertices.any { it.x == ptA.x && it.z == ptA.z }) ptA
                           else if (existingVertices.any { it.x == ptB.x && it.z == ptB.z }) ptB
                           else return
            val newVertex = if (oldVertex == ptA) ptB else ptA
            val newPolygon = evaluateModifyPolygonReplace(oldVertex, newVertex, existingVertices) ?: return
            for (i in newPolygon.indices) {
                emitLineSurface(player, newPolygon[i], newPolygon[(i + 1) % newPolygon.size])
            }
            emitLineSurface(player, oldVertex, newVertex)
        }
        3 -> {
            val adj1 = newPoints[0]; val adj2 = newPoints[1]; val ins = newPoints[2]
            val newPolygon = evaluateModifyPolygonExplicitInsert(adj1, adj2, ins, existingVertices) ?: return
            for (i in newPolygon.indices) {
                emitLineSurface(player, newPolygon[i], newPolygon[(i + 1) % newPolygon.size])
            }
        }
    }
}
