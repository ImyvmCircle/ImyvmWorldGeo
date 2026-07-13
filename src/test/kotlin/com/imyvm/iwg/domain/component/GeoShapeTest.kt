package com.imyvm.iwg.domain.component

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
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
        assertFalse(com.imyvm.iwg.util.geo.circleContainsPoint(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `legacy parameter getter is a detached snapshot`() {
        val shape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 10))
        val snapshot = shape.shapeParameter

        snapshot[2] = 20

        assertEquals(listOf(0, 0, 10), shape.shapeParameter)
    }

    @Test
    fun `legacy setters cannot leave a partially invalid geometry`() {
        val shape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 10))

        assertFails { shape.geoShapeType = GeoShapeType.RECTANGLE }
        assertFails { shape.shapeParameter = mutableListOf(0, 0, -1) }

        assertEquals(GeoShapeType.CIRCLE, shape.geoShapeType)
        assertEquals(listOf(0, 0, 10), shape.shapeParameter)
    }

    @Test
    fun `polygon construction rejects duplicate degenerate and concave vertices`() {
        assertFails {
            GeoShape(GeoShapeType.POLYGON, mutableListOf(0, 0, 10, 0, 10, 0, 0, 10))
        }
        assertFails {
            GeoShape(GeoShapeType.POLYGON, mutableListOf(0, 0, 10, 0, 20, 0))
        }
        assertFails {
            GeoShape(GeoShapeType.POLYGON, mutableListOf(0, 0, 10, 0, 5, 5, 10, 10, 0, 10))
        }
        assertFails {
            GeoShape(GeoShapeType.POLYGON, mutableListOf(0, 10, 6, -8, -10, 3, 10, 3, -6, -8))
        }
    }

    @Test
    fun `polygon containment handles int boundary coordinates`() {
        val polygon = GeoShape(
            GeoShapeType.POLYGON,
            mutableListOf(
                Int.MIN_VALUE, Int.MIN_VALUE,
                Int.MAX_VALUE, Int.MIN_VALUE,
                Int.MAX_VALUE, Int.MAX_VALUE,
                Int.MIN_VALUE, Int.MAX_VALUE
            )
        )

        assertTrue(polygon.containsPoint(0, 0))
        assertTrue(polygon.containsPoint(0, Int.MIN_VALUE))
    }
}
