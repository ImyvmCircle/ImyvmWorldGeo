package com.imyvm.iwg.util.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IterationTest {
    @Test
    fun `huge grid yields nearest points without materializing the area`() {
        val points = generateShapePointSequence(Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE) { _, _ -> true }
            .take(9)
            .toList()
        val distances = points.map { (x, z) -> x.toLong() * x + z.toLong() * z }

        assertEquals(9, points.size)
        assertTrue(distances.zipWithNext().all { (left, right) -> left <= right })
    }

    @Test
    fun `distance ordering remains exact beyond double and ulong range`() {
        val near = exactSquaredDistance(Int.MAX_VALUE.toLong(), Int.MAX_VALUE - 1L, Int.MIN_VALUE, Int.MIN_VALUE)
        val far = exactSquaredDistance(Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong(), Int.MIN_VALUE, Int.MIN_VALUE)

        assertTrue(near < far)
        assertTrue(far.carry)
    }
}
