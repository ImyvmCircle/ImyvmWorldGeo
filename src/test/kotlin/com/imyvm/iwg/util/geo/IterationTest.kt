package com.imyvm.iwg.util.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IterationTest {
    @Test
    fun `huge grid yields nearest points without materializing the area`() {
        val points = generateShapePoints(Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE) { _, _ -> true }
            .take(9)
            .toList()
        val distances = points.map { (x, z) -> x.toLong() * x + z.toLong() * z }

        assertEquals(9, points.size)
        assertTrue(distances.zipWithNext().all { (left, right) -> left <= right })
    }
}
