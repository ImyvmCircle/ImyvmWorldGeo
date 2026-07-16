package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.selection.getEffectiveShapeType
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.component.ShapeGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.isConvex
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

private enum class BoundaryStyle {
    SELECTION,
    ORIGINAL,
    MODIFYING
}

internal fun displaySelectionAndScopeBoundariesForAllPlayers(server: MinecraftServer) {
    for (player in server.playerList.players) {
        val session = SelectionDisplaySession()
        ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.let { displayForPlayer(player, it, session) }
        if (player.uuid in ImyvmWorldGeo.locationActionBarEnabledPlayers && !session.exhausted) {
            displayActionBarScopes(player, session)
        }
    }
}

fun displaySelectionForAllPlayers(server: MinecraftServer) {
    for (player in server.playerList.players) {
        val state = ImyvmWorldGeo.pointSelectingPlayers[player.uuid] ?: continue
        displayForPlayer(player, state, SelectionDisplaySession())
    }
}

fun displayScopeBoundariesForActionBarPlayers(server: MinecraftServer) {
    for (player in server.playerList.players) {
        if (player.uuid !in ImyvmWorldGeo.locationActionBarEnabledPlayers) continue
        displayActionBarScopes(player, SelectionDisplaySession())
    }
}

private fun displayActionBarScopes(
    player: ServerPlayer,
    session: SelectionDisplaySession
) {
    val modifyingScope = (
        ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape
            as? HypotheticalShape.ModifyExisting
        )?.scope
    val playerWorldId = player.level().dimension().identifier()
    displayRegionScopeCandidates(RegionDatabase.getRegionList(), session) { scope ->
        if (scope !== modifyingScope && scope.geoShape != null && scope.worldId == playerWorldId) {
            displayOriginalScope(player, scope, session)
        }
    }
    commitPillars(player, session)
}

internal fun displayScopeCandidates(
    scopes: Iterable<GeoScope>,
    session: SelectionDisplaySession,
    display: (GeoScope) -> Unit
) {
    val iterator = scopes.iterator()
    while (session.tryUseSurface()) {
        if (!iterator.hasNext()) return
        display(iterator.next())
    }
}

internal fun displayRegionScopeCandidates(
    regions: Iterable<Region>,
    session: SelectionDisplaySession,
    display: (GeoScope) -> Unit
) {
    val regionIterator = regions.iterator()
    while (session.tryUseSurface()) {
        if (!regionIterator.hasNext()) return
        val scopeIterator = regionIterator.next().scopes.iterator()
        while (session.tryUseSurface()) {
            if (!scopeIterator.hasNext()) break
            display(scopeIterator.next())
        }
    }
}

private fun displayForPlayer(
    player: ServerPlayer,
    state: SelectionState,
    session: SelectionDisplaySession
) {
    val points = state.points
    for (point in points) emitPillar(player, point.x, point.z, session)

    if (points.isEmpty()) {
        val shape = state.hypotheticalShape
        if (shape is HypotheticalShape.ModifyExisting) {
            displayModifyingScope(player, shape.scope, session)
        }
        commitPillars(player, session)
        return
    }

    when (val shape = state.hypotheticalShape) {
        is HypotheticalShape.ModifyExisting -> displayForModifyExisting(player, points, shape.scope, session)
        is HypotheticalShape.Normal -> displayForShape(player, points, shape.shapeType, session)
        null -> displayForShape(player, points, state.getEffectiveShapeType(), session)
    }
    commitPillars(player, session)
}

private fun displayForShape(
    player: ServerPlayer,
    points: List<BlockPos>,
    shapeType: GeoShapeType,
    session: SelectionDisplaySession
) {
    when (shapeType) {
        GeoShapeType.CIRCLE -> displayCircleSelection(player, points, session)
        GeoShapeType.RECTANGLE -> displayRectangleSelection(player, points, session)
        GeoShapeType.POLYGON -> displayPolygonSelection(player, points, session)
        GeoShapeType.UNKNOWN -> Unit
    }
}

private fun displayCircleSelection(
    player: ServerPlayer,
    points: List<BlockPos>,
    session: SelectionDisplaySession
) {
    if (points.size < 2) return
    val shape = evaluateCircleShape(points[0], points[1]) ?: return
    val circle = shape.typedGeometry as CircleGeometry
    val lineSamples = maxOf(1, session.surfaceUnits / 4)
    emitLineSurface(player, points[0], points[1], session, lineSamples)
    drawCircleOutline(player, circle.centerX, circle.centerZ, circle.radius, session)
}

private fun displayRectangleSelection(
    player: ServerPlayer,
    points: List<BlockPos>,
    session: SelectionDisplaySession
) {
    if (points.size < 2) return
    val shape = evaluateRectangleShape(points[0], points[1]) ?: return
    val rect = shape.typedGeometry as RectangleGeometry
    val corners = arrayOf(
        BlockPos(rect.west, 0, rect.north),
        BlockPos(rect.east, 0, rect.north),
        BlockPos(rect.east, 0, rect.south),
        BlockPos(rect.west, 0, rect.south)
    )
    corners.forEach { emitPillar(player, it.x, it.z, session) }
    emitClosedBoundary(player, corners.size, { corners[it].x }, { corners[it].z }, BoundaryStyle.SELECTION, session)
}

private fun displayPolygonSelection(
    player: ServerPlayer,
    points: List<BlockPos>,
    session: SelectionDisplaySession
) {
    if (points.size < 3 ||
        !isPolygonVertexCountSupported(points.size) ||
        !isConvex(points) ||
        checkPolygonSize(points) != null
    ) return
    emitClosedBoundary(player, points.size, { points[it].x }, { points[it].z }, BoundaryStyle.SELECTION, session)
}

private fun displayForModifyExisting(
    player: ServerPlayer,
    newPoints: List<BlockPos>,
    scope: GeoScope,
    session: SelectionDisplaySession
) {
    displayModifyingScope(player, scope, session)
    when (val geometry = scope.geoShape?.typedGeometry ?: return) {
        is RectangleGeometry -> displayModifyRectanglePreview(player, newPoints, geometry, session)
        is CircleGeometry -> displayModifyCirclePreview(player, newPoints, geometry, session)
        is PolygonGeometry -> displayModifyPolygonPreview(player, newPoints, geometry, session)
        UnknownGeometry -> Unit
    }
}

fun displayScopeBoundaryForPlayer(player: ServerPlayer, scope: GeoScope) {
    val session = SelectionDisplaySession()
    displayOriginalScope(player, scope, session)
    commitPillars(player, session)
}

fun displayScopeBoundariesForPlayer(player: ServerPlayer, scopes: List<GeoScope>) {
    val session = SelectionDisplaySession()
    displayScopeCandidates(scopes, session) { displayOriginalScope(player, it, session) }
    commitPillars(player, session)
}

internal fun displayRegionScopeBoundariesForPlayer(player: ServerPlayer, regions: List<Region>) {
    val session = SelectionDisplaySession()
    val playerWorldId = player.level().dimension().identifier()
    displayRegionScopeCandidates(regions, session) { scope ->
        if (scope.geoShape != null && scope.worldId == playerWorldId) {
            displayOriginalScope(player, scope, session)
        }
    }
    commitPillars(player, session)
}

fun displayModifyingScope(player: ServerPlayer, scope: GeoScope) {
    val session = SelectionDisplaySession()
    displayModifyingScope(player, scope, session)
    commitPillars(player, session)
}

private fun displayModifyingScope(
    player: ServerPlayer,
    scope: GeoScope,
    session: SelectionDisplaySession
) {
    displayGeometry(player, scope.geoShape?.typedGeometry ?: return, BoundaryStyle.MODIFYING, session)
}

fun displayOriginalScope(player: ServerPlayer, scope: GeoScope) {
    val session = SelectionDisplaySession()
    displayOriginalScope(player, scope, session)
    commitPillars(player, session)
}

private fun displayOriginalScope(
    player: ServerPlayer,
    scope: GeoScope,
    session: SelectionDisplaySession
) {
    displayGeometry(player, scope.geoShape?.typedGeometry ?: return, BoundaryStyle.ORIGINAL, session)
}

private fun displayGeometry(
    player: ServerPlayer,
    geometry: ShapeGeometry,
    style: BoundaryStyle,
    session: SelectionDisplaySession
) {
    when (geometry) {
        is RectangleGeometry -> {
            val x = intArrayOf(geometry.west, geometry.east, geometry.east, geometry.west)
            val z = intArrayOf(geometry.north, geometry.north, geometry.south, geometry.south)
            for (index in x.indices) emitPillar(player, x[index], z[index], session)
            emitClosedBoundary(player, x.size, x::get, z::get, style, session)
        }
        is CircleGeometry -> {
            emitPillar(player, geometry.centerX, geometry.centerZ, session)
            emitPillar(player, geometry.centerX, geometry.centerZ + geometry.radius, session)
            when (style) {
                BoundaryStyle.SELECTION -> drawCircleOutline(player, geometry.centerX, geometry.centerZ, geometry.radius, session)
                BoundaryStyle.ORIGINAL -> drawCircleOutlineOld(player, geometry.centerX, geometry.centerZ, geometry.radius, session)
                BoundaryStyle.MODIFYING -> drawCircleOutlineModifying(player, geometry.centerX, geometry.centerZ, geometry.radius, session)
            }
        }
        is PolygonGeometry -> {
            queueSampledPillars(player, geometry.vertexCount, geometry::x, geometry::z, session)
            emitClosedBoundary(player, geometry.vertexCount, geometry::x, geometry::z, style, session)
        }
        UnknownGeometry -> Unit
    }
}

private fun queueSampledPillars(
    player: ServerPlayer,
    vertexCount: Int,
    x: (Int) -> Int,
    z: (Int) -> Int,
    session: SelectionDisplaySession
) {
    // Queueing costs one unit and reserves one send; keep the other half for the outline.
    val sampleCount = minOf(vertexCount, session.surfaceUnits / 4)
    for (sample in 0 until sampleCount) {
        val index = evenlySpacedIndex(sample, sampleCount, vertexCount)
        emitPillar(player, x(index), z(index), session)
    }
}

private fun emitClosedBoundary(
    player: ServerPlayer,
    vertexCount: Int,
    x: (Int) -> Int,
    z: (Int) -> Int,
    style: BoundaryStyle,
    session: SelectionDisplaySession
) {
    if (vertexCount < 2 || session.surfaceUnits == 0) return
    val sampledEdges = minOf(vertexCount, session.surfaceUnits)
    for (sample in 0 until sampledEdges) {
        val edge = evenlySpacedIndex(sample, sampledEdges, vertexCount)
        val next = (edge + 1) % vertexCount
        val edgesLeft = sampledEdges - sample
        val maxSamples = maxOf(1, session.surfaceUnits / edgesLeft)
        when (style) {
            BoundaryStyle.SELECTION -> emitLineSurface(player, x(edge), z(edge), x(next), z(next), session, maxSamples)
            BoundaryStyle.ORIGINAL -> emitLineSurfaceOld(player, x(edge), z(edge), x(next), z(next), session, maxSamples)
            BoundaryStyle.MODIFYING -> emitLineSurfaceModifying(player, x(edge), z(edge), x(next), z(next), session, maxSamples)
        }
        if (session.surfaceUnits == 0) return
    }
}

internal fun evenlySpacedIndex(sample: Int, sampleCount: Int, totalCount: Int): Int {
    require(sample in 0 until sampleCount && sampleCount > 0 && totalCount > 0)
    if (sampleCount == 1) return totalCount / 2
    return (sample.toLong() * (totalCount - 1) / (sampleCount - 1)).toInt()
}

private fun displayModifyRectanglePreview(
    player: ServerPlayer,
    newPoints: List<BlockPos>,
    geometry: RectangleGeometry,
    session: SelectionDisplaySession
) {
    if (newPoints.size != 1) return
    val shape = evaluateModifyRectangle(newPoints[0], geometry) ?: return
    val rect = shape.typedGeometry as RectangleGeometry
    val x = intArrayOf(rect.west, rect.east, rect.east, rect.west)
    val z = intArrayOf(rect.north, rect.north, rect.south, rect.south)
    for (index in x.indices) emitPillar(player, x[index], z[index], session)
    emitClosedBoundary(player, x.size, x::get, z::get, BoundaryStyle.SELECTION, session)
}

private fun displayModifyCirclePreview(
    player: ServerPlayer,
    newPoints: List<BlockPos>,
    geometry: CircleGeometry,
    session: SelectionDisplaySession
) {
    when (newPoints.size) {
        1 -> {
            val point = newPoints[0]
            if (point.x == geometry.centerX && point.z == geometry.centerZ) return
            val shape = evaluateModifyCircleRadius(point, geometry) ?: return
            val circle = shape.typedGeometry as CircleGeometry
            drawCircleOutline(player, circle.centerX, circle.centerZ, circle.radius, session)
        }
        2 -> {
            val oldCenter = newPoints[0]
            val newCenter = newPoints[1]
            val shape = evaluateModifyCircleCenter(oldCenter, newCenter, geometry) ?: return
            val circle = shape.typedGeometry as CircleGeometry
            val circleSamples = maxOf(1, session.surfaceUnits * 3 / 4)
            drawCircleOutline(player, circle.centerX, circle.centerZ, circle.radius, session, circleSamples)
            emitLineSurface(player, oldCenter, newCenter, session)
        }
    }
}

private fun displayModifyPolygonPreview(
    player: ServerPlayer,
    newPoints: List<BlockPos>,
    geometry: PolygonGeometry,
    session: SelectionDisplaySession
) {
    if (!session.tryUseSurface(geometry.vertexCount)) return
    val existingVertices = com.imyvm.iwg.application.region.geometryToBlockPosList(geometry)
    when (newPoints.size) {
        1 -> {
            val point = newPoints[0]
            if (existingVertices.any { it.x == point.x && it.z == point.z }) return
            val shape = evaluateModifyPolygonInsert(point, geometry) ?: return
            val polygon = shape.typedGeometry as PolygonGeometry
            val vertices = com.imyvm.iwg.application.region.geometryToBlockPosList(polygon)
            val index = vertices.indexOfFirst { it.x == point.x && it.z == point.z }
            if (index == -1) return
            emitLineSurface(player, vertices[(index - 1 + vertices.size) % vertices.size], point, session)
            emitLineSurface(player, point, vertices[(index + 1) % vertices.size], session)
        }
        2 -> {
            val oldVertex = newPoints[0]
            val newVertex = newPoints[1]
            val shape = evaluateModifyPolygonReplace(oldVertex, newVertex, geometry) ?: return
            val polygon = shape.typedGeometry as PolygonGeometry
            emitClosedBoundary(player, polygon.vertexCount, polygon::x, polygon::z, BoundaryStyle.SELECTION, session)
            emitLineSurface(player, oldVertex, newVertex, session)
        }
        3 -> {
            val shape = evaluateModifyPolygonExplicitInsert(
                newPoints[0],
                newPoints[1],
                newPoints[2],
                geometry
            ) ?: return
            val polygon = shape.typedGeometry as PolygonGeometry
            emitClosedBoundary(player, polygon.vertexCount, polygon::x, polygon::z, BoundaryStyle.SELECTION, session)
        }
    }
}
