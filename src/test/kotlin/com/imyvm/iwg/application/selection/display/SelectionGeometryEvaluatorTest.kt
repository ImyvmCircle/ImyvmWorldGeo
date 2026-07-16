package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.domain.component.*
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionGeometryEvaluatorTest {
    @Test
    fun `circle preview rejects reversed center movement and coordinate overflow`() {
        val geometry = CircleGeometry(0, 0, 100)

        assertNull(evaluateModifyCircleCenter(BlockPos(1, 0, 1), BlockPos.ZERO, geometry))
        val moved = assertNotNull(evaluateModifyCircleCenter(BlockPos.ZERO, BlockPos(100, 0, 0), geometry))
        val movedCircle = moved.typedGeometry as CircleGeometry
        assertTrue(movedCircle.centerX == 100 && movedCircle.centerZ == 0 && movedCircle.radius == 100)
        assertNull(evaluateModifyCircleCenter(BlockPos.ZERO, BlockPos(Int.MAX_VALUE - 5, 0, 0), geometry))
        assertNull(evaluateCircleShape(BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE), BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)))

        val overflowGeometry = CircleGeometry(Int.MAX_VALUE - 10, 0, 10)
        assertNull(evaluateModifyCircleRadius(BlockPos(Int.MAX_VALUE - 30, 0, 0), overflowGeometry))
    }

    @Test
    fun `rectangle preview keeps extreme coordinate spans exact`() {
        val shape = assertNotNull(evaluateRectangleShape(
            BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE),
            BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)
        ))
        val rect = shape.typedGeometry as RectangleGeometry
        assertTrue(rect.west == Int.MIN_VALUE && rect.north == Int.MIN_VALUE && rect.east == Int.MAX_VALUE && rect.south == Int.MAX_VALUE)

        val existingGeometry = RectangleGeometry(Int.MIN_VALUE, Int.MIN_VALUE, 0, 0)
        val modified = assertNotNull(evaluateModifyRectangle(BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE), existingGeometry))
        val modRect = modified.typedGeometry as RectangleGeometry
        assertTrue(modRect.west == Int.MIN_VALUE && modRect.north == Int.MIN_VALUE && modRect.east == Int.MAX_VALUE && modRect.south == Int.MAX_VALUE)
    }

    @Test
    fun `automatic polygon insertion uses nearest segment instead of nearest midpoint`() {
        val geometry = PolygonGeometry(intArrayOf(0, 0, 1_000, 0, 1_000, 300, 0, 300))
        val inserted = BlockPos(100, 0, -20)

        val result = assertNotNull(evaluateModifyPolygonInsert(inserted, geometry))
        val polygon = result.typedGeometry as PolygonGeometry
        assertTrue(polygon.x(1) == 100 && polygon.z(1) == -20)
    }

    @Test
    fun `polygon preview rejects invalid source and nonadjacent insertion`() {
        val geometry = PolygonGeometry(intArrayOf(0, 0, 100, 0, 100, 100, 0, 100))

        assertNull(evaluateModifyPolygonReplace(BlockPos(50, 0, 50), BlockPos(60, 0, 60), geometry))
        assertNull(evaluateModifyPolygonReplace(BlockPos(0, 0, 0), BlockPos(100, 0, 0), geometry))
        assertNull(evaluateModifyPolygonExplicitInsert(BlockPos(0, 0, 0), BlockPos(100, 0, 100), BlockPos(50, 0, -10), geometry))
    }
}
