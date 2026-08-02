package com.imyvm.iwg.util.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class PointDistanceTest {
    @Test
    fun `point inside box has zero distance`() {
        assertEquals(0L, squaredDistanceToBox(5, 5, 0, 0, 10, 10))
        assertEquals(0L, squaredDistanceToBox(0, 10, 0, 0, 10, 10))
    }

    @Test
    fun `distance is measured to the nearest edge or corner`() {
        assertEquals(25L, squaredDistanceToBox(15, 5, 0, 0, 10, 10))
        assertEquals(50L, squaredDistanceToBox(15, 15, 0, 0, 10, 10))
        assertEquals(25L, squaredDistanceToBox(-5, 5, 0, 0, 10, 10))
    }

    @Test
    fun `extreme coordinates saturate instead of overflowing`() {
        assertEquals(
            Long.MAX_VALUE,
            squaredDistanceToBox(Int.MIN_VALUE, 0, Int.MAX_VALUE, -1, Int.MAX_VALUE, 1)
        )
        assertEquals(
            Long.MAX_VALUE,
            squaredDistanceToBox(0, Int.MIN_VALUE, -1, Int.MAX_VALUE, 1, Int.MAX_VALUE)
        )
    }
}
