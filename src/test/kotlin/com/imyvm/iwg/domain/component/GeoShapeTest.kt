package com.imyvm.iwg.domain.component

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoShapeTest {
    @Test
    fun `invalid parameters fail explicitly`() {
        assertFails { GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0)) }
        assertFails { GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, -1)) }
        assertFails { GeoShape(GeoShapeType.RECTANGLE, mutableListOf(10, 0, 0, 10)) }
        assertFails { GeoShape(GeoShapeType.POLYGON, mutableListOf(0, 0, 1, 1)) }
    }

    @Test
    fun `circle containment handles int boundaries without overflow`() {
        val circle = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, Int.MAX_VALUE))

        assertTrue(circle.containsPoint(Int.MAX_VALUE, 0))
        assertFalse(circle.containsPoint(Int.MIN_VALUE, 0))
    }
}
