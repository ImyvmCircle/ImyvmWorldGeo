package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.application.region.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import net.minecraft.core.BlockPos

internal fun evaluateRectangleShape(pos1: BlockPos, pos2: BlockPos): GeoShape? {
    if (pos1.x == pos2.x || pos1.z == pos2.z) return null
    val result = constructShape(listOf(pos1, pos2), GeoShapeType.RECTANGLE)
    return (result as? Result.Ok)?.value
}

internal fun evaluateCircleShape(center: BlockPos, edge: BlockPos): GeoShape? {
    if (center.x == edge.x && center.z == edge.z) return null
    val result = constructShape(listOf(center, edge), GeoShapeType.CIRCLE)
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyRectangle(newPoint: BlockPos, geometry: RectangleGeometry): GeoShape? {
    val result = modifyRectangle(geometry, newPoint)
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyCircleRadius(newPoint: BlockPos, geometry: CircleGeometry): GeoShape? {
    val result = modifyCircleRadius(geometry, newPoint)
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyCircleCenter(oldCenter: BlockPos, newCenter: BlockPos, geometry: CircleGeometry): GeoShape? {
    if (oldCenter.x != geometry.centerX || oldCenter.z != geometry.centerZ) return null
    val result = modifyCircleCenter(geometry, newCenter)
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyPolygonInsert(newPoint: BlockPos, geometry: PolygonGeometry): GeoShape? {
    if (geometry.vertexCount >= MAX_POLYGON_VERTICES) return null
    val existingPoints = geometryToBlockPosList(geometry)
    val (adjacentA, adjacentB) = findNearestAdjacentPoints(existingPoints, newPoint)
    val result = modifyPolygonInsert(geometry, adjacentA, adjacentB, newPoint) ?: return null
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyPolygonReplace(existingVertex: BlockPos, newPoint: BlockPos, geometry: PolygonGeometry): GeoShape? {
    val result = modifyPolygonMove(geometry, existingVertex, newPoint) ?: return null
    return (result as? Result.Ok)?.value
}

internal fun evaluateModifyPolygonExplicitInsert(adj1: BlockPos, adj2: BlockPos, newPoint: BlockPos, geometry: PolygonGeometry): GeoShape? {
    if (geometry.vertexCount >= MAX_POLYGON_VERTICES) return null
    val result = modifyPolygonInsert(geometry, adj1, adj2, newPoint) ?: return null
    return (result as? Result.Ok)?.value
}
