package com.imyvm.iwg.application.selection.display

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelectionGeometryEvaluatorTest {
    @Test
    fun `circle preview rejects reversed center movement and coordinate overflow`() {
        val existing = listOf(0, 0, 100)

        assertNull(evaluateModifyCircleCenter(BlockPos(1, 0, 1), BlockPos.ZERO, existing))
        assertEquals(
            listOf(100, 0, 100),
            evaluateModifyCircleCenter(BlockPos.ZERO, BlockPos(100, 0, 0), existing)
        )
        assertNull(evaluateModifyCircleCenter(BlockPos.ZERO, BlockPos(Int.MAX_VALUE - 5, 0, 0), existing))
        assertNull(evaluateCircleShape(BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE), BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)))
        assertNull(
            evaluateModifyCircleRadius(
                BlockPos(Int.MAX_VALUE - 30, 0, 0),
                listOf(Int.MAX_VALUE - 10, 0, 10)
            )
        )
    }

    @Test
    fun `rectangle preview keeps extreme coordinate spans exact`() {
        assertEquals(
            listOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
            evaluateRectangleShape(
                BlockPos(Int.MIN_VALUE, 0, Int.MIN_VALUE),
                BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE)
            )
        )
        assertEquals(
            listOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
            evaluateModifyRectangle(
                BlockPos(Int.MAX_VALUE, 0, Int.MAX_VALUE),
                listOf(Int.MIN_VALUE, Int.MIN_VALUE, 0, 0)
            )
        )
    }

    @Test
    fun `automatic polygon insertion uses nearest segment instead of nearest midpoint`() {
        val polygon = listOf(
            BlockPos(0, 0, 0),
            BlockPos(1_000, 0, 0),
            BlockPos(1_000, 0, 300),
            BlockPos(0, 0, 300)
        )
        val inserted = BlockPos(100, 0, -20)

        val result = assertNotNull(evaluateModifyPolygonInsert(inserted, polygon))

        assertEquals(inserted, result[1])
    }

    @Test
    fun `polygon preview rejects invalid source and nonadjacent insertion`() {
        val polygon = listOf(
            BlockPos(0, 0, 0),
            BlockPos(100, 0, 0),
            BlockPos(100, 0, 100),
            BlockPos(0, 0, 100)
        )

        assertNull(evaluateModifyPolygonReplace(BlockPos(50, 0, 50), BlockPos(60, 0, 60), polygon))
        assertNull(evaluateModifyPolygonReplace(polygon[0], polygon[1], polygon))
        assertNull(evaluateModifyPolygonExplicitInsert(polygon[0], polygon[2], BlockPos(50, 0, -10), polygon))
    }
}
