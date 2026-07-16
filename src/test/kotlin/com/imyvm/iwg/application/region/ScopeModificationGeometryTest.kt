package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.*
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScopeModificationGeometryTest {

    @Test
    fun `modifyCircleRadius produces same shape as constructShape with center and edge`() {
        val circle = GeoShape.circle(GeoPoint(10, 20), 50).typedGeometry as CircleGeometry
        val edgePoint = BlockPos(110, 0, 20)

        val typedResult = modifyCircleRadius(circle, edgePoint)
        val directResult = constructShape(
            listOf(BlockPos(10, 0, 20), edgePoint),
            GeoShapeType.CIRCLE
        )

        assertIs<Result.Ok<GeoShape>>(typedResult)
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyCircleCenter produces same shape as constructShape with new center and edge`() {
        val circle = GeoShape.circle(GeoPoint(10, 20), 50).typedGeometry as CircleGeometry
        val newCenter = BlockPos(30, 0, 40)

        val typedResult = modifyCircleCenter(circle, newCenter)
        val directResult = constructShape(
            listOf(newCenter, BlockPos(80, 0, 40)),
            GeoShapeType.CIRCLE
        )

        assertIs<Result.Ok<GeoShape>>(typedResult)
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyRectangle produces same shape as constructShape with adjusted corners`() {
        val rect = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100)).typedGeometry as RectangleGeometry
        val point = BlockPos(20, 0, 30)

        val typedResult = modifyRectangle(rect, point)
        assertIs<Result.Ok<GeoShape>>(typedResult)

        val newGeom = typedResult.value.typedGeometry as RectangleGeometry
        val directResult = constructShape(
            listOf(BlockPos(newGeom.west, 0, newGeom.north), BlockPos(newGeom.east, 0, newGeom.south)),
            GeoShapeType.RECTANGLE
        )
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyPolygonMove produces same shape as constructShape with replaced vertex`() {
        val polygon = GeoShape.polygon(listOf(GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(100, 200)))
        val geometry = polygon.typedGeometry as PolygonGeometry
        val oldPoint = BlockPos(200, 0, 0)
        val newPoint = BlockPos(190, 0, 10)

        val modification = modifyPolygonMove(geometry, oldPoint, newPoint)
        val typedResult = assertIs<PolygonModificationResult.Shape>(modification).result
        assertIs<Result.Ok<GeoShape>>(typedResult)

        val directResult = constructShape(
            listOf(BlockPos(0, 0, 0), BlockPos(190, 0, 10), BlockPos(100, 0, 200)),
            GeoShapeType.POLYGON
        )
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyPolygonDelete produces same shape as constructShape without removed vertex`() {
        val polygon = GeoShape.polygon(listOf(
            GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(200, 200), GeoPoint(0, 200)
        ))
        val geometry = polygon.typedGeometry as PolygonGeometry
        val point = BlockPos(200, 0, 0)

        val modification = modifyPolygonDelete(geometry, point)
        val typedResult = assertIs<PolygonModificationResult.Shape>(modification).result
        assertIs<Result.Ok<GeoShape>>(typedResult)

        val directResult = constructShape(
            listOf(BlockPos(0, 0, 0), BlockPos(200, 0, 200), BlockPos(0, 0, 200)),
            GeoShapeType.POLYGON
        )
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyPolygonInsert produces same shape as constructShape with vertex inserted`() {
        val polygon = GeoShape.polygon(listOf(GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(100, 200)))
        val geometry = polygon.typedGeometry as PolygonGeometry
        val adj1 = BlockPos(0, 0, 0)
        val adj2 = BlockPos(200, 0, 0)
        val newPoint = BlockPos(100, 0, -50)

        val modification = modifyPolygonInsert(geometry, adj1, adj2, newPoint)
        val typedResult = assertIs<PolygonModificationResult.Shape>(modification).result
        assertIs<Result.Ok<GeoShape>>(typedResult)

        val directResult = constructShape(
            listOf(BlockPos(0, 0, 0), BlockPos(100, 0, -50), BlockPos(200, 0, 0), BlockPos(100, 0, 200)),
            GeoShapeType.POLYGON
        )
        assertIs<Result.Ok<GeoShape>>(directResult)
        assertEquals(directResult.value.shapeParameter, typedResult.value.shapeParameter)
    }

    @Test
    fun `modifyPolygonMove returns null when point not found`() {
        val polygon = GeoShape.polygon(listOf(GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(100, 200)))
        val geometry = polygon.typedGeometry as PolygonGeometry

        assertEquals(
            PolygonModificationResult.PointNotFound,
            modifyPolygonMove(geometry, BlockPos(999, 0, 999), BlockPos(50, 0, 50))
        )
    }

    @Test
    fun `modifyPolygonMove rejects identity move as DuplicatedPoints`() {
        val polygon = GeoShape.polygon(listOf(GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(100, 200)))
        val geometry = polygon.typedGeometry as PolygonGeometry
        val point = BlockPos(200, 0, 0)

        val modification = assertIs<PolygonModificationResult.Shape>(modifyPolygonMove(geometry, point, point))
        val result = modification.result
        assertIs<Result.Err<CreationError>>(result)
        assertEquals(CreationError.DuplicatedPoints, result.error)
    }

    @Test
    fun `polygon insert distinguishes missing and nonadjacent vertices`() {
        val polygon = GeoShape.polygon(listOf(
            GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(200, 200), GeoPoint(0, 200)
        ))
        val geometry = polygon.typedGeometry as PolygonGeometry

        assertEquals(
            PolygonModificationResult.PointNotFound,
            modifyPolygonInsert(
                geometry,
                BlockPos(999, 0, 999),
                BlockPos(200, 0, 0),
                BlockPos(100, 0, -50)
            )
        )
        assertEquals(
            PolygonModificationResult.PointsNotAdjacent,
            modifyPolygonInsert(
                geometry,
                BlockPos(0, 0, 0),
                BlockPos(200, 0, 200),
                BlockPos(100, 0, -50)
            )
        )
    }

    @Test
    fun `polygon delete distinguishes minimum size and missing vertex`() {
        val triangle = GeoShape.polygon(listOf(
            GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(100, 200)
        )).typedGeometry as PolygonGeometry
        val rectangle = GeoShape.polygon(listOf(
            GeoPoint(0, 0), GeoPoint(200, 0), GeoPoint(200, 200), GeoPoint(0, 200)
        )).typedGeometry as PolygonGeometry

        assertEquals(
            PolygonModificationResult.MinimumPoints,
            modifyPolygonDelete(triangle, BlockPos(0, 0, 0))
        )
        assertEquals(
            PolygonModificationResult.PointNotFound,
            modifyPolygonDelete(rectangle, BlockPos(999, 0, 999))
        )
    }

    @Test
    fun `modifyCircleCenter rejects overflow as CoordinateRangeExceeded`() {
        val circle = GeoShape.circle(GeoPoint(0, 0), Int.MAX_VALUE).typedGeometry as CircleGeometry
        val newCenter = BlockPos(1, 0, 0)

        val result = modifyCircleCenter(circle, newCenter)
        assertIs<Result.Err<CreationError>>(result)
        assertEquals(CreationError.CoordinateRangeExceeded, result.error)
    }
}
