package com.imyvm.iwg.domain.component

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GeoShapeTest {
    @Test
    fun `named factories encode typed shape inputs`() {
        val circle = GeoShape.circle(GeoPoint(3, 4), 5)
        val rectangle = GeoShape.rectangle(GeoPoint(10, 20), GeoPoint(-10, -20))
        val vertices = mutableListOf(GeoPoint(0, 0), GeoPoint(10, 0), GeoPoint(0, 10))
        val polygon = GeoShape.polygon(vertices)

        vertices[0] = GeoPoint(100, 100)

        assertEquals(GeoShapeType.CIRCLE, circle.geoShapeType)
        assertEquals(listOf(3, 4, 5), circle.shapeParameter)
        assertEquals(listOf(-10, -20, 10, 20), rectangle.shapeParameter)
        assertEquals(listOf(0, 0, 10, 0, 0, 10), polygon.shapeParameter)
    }

    @Test
    fun `bounded geometries expose inclusive bounds and containment work`() {
        val circle = assertIs<BoundedShapeGeometry>(GeoShape.circle(GeoPoint(3, 4), 5).typedGeometry)
        val rectangle = assertIs<BoundedShapeGeometry>(
            GeoShape.rectangle(GeoPoint(10, 20), GeoPoint(-10, -20)).typedGeometry
        )
        val polygon = assertIs<BoundedShapeGeometry>(
            GeoShape.polygon(listOf(GeoPoint(-4, 2), GeoPoint(8, -3), GeoPoint(5, 9))).typedGeometry
        )

        assertEquals(ShapeBounds(-2, -1, 8, 9), circle.bounds())
        assertEquals(1, circle.containmentWorkUnits)
        assertEquals(ShapeBounds(-10, -20, 10, 20), rectangle.bounds())
        assertEquals(1, rectangle.containmentWorkUnits)
        assertEquals(ShapeBounds(-4, -3, 8, 9), polygon.bounds())
        assertEquals(3, polygon.containmentWorkUnits)
        assertFalse(GeoShape(GeoShapeType.UNKNOWN, mutableListOf()).typedGeometry is BoundedShapeGeometry)
    }

    @Test
    fun `polygon factory bounds work before flattening vertices`() {
        assertFailsWith<ArithmeticException> {
            GeoShape.circle(GeoPoint(Int.MAX_VALUE, 0), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            GeoShape.polygon(List(MAX_POLYGON_VERTICES + 1) { GeoPoint(it, it) })
        }
    }

    @Test
    fun `fallback radius is bounded before searching`() {
        assertEquals(0, requireTeleportFallbackSearchRadius(0))
        assertEquals(
            MAX_TELEPORT_FALLBACK_SEARCH_RADIUS,
            requireTeleportFallbackSearchRadius(MAX_TELEPORT_FALLBACK_SEARCH_RADIUS)
        )
        assertFailsWith<IllegalArgumentException> { requireTeleportFallbackSearchRadius(-1) }
        assertFailsWith<IllegalArgumentException> {
            requireTeleportFallbackSearchRadius(MAX_TELEPORT_FALLBACK_SEARCH_RADIUS + 1)
        }
    }

    @Test
    fun `fallback candidates are ordered by Manhattan distance`() {
        val center = net.minecraft.core.BlockPos.ZERO
        val verticallyNear = center.above()
        val sameLayerFar = center.offset(8, 0, 8)

        val result = findClosestMatchingBlockPos(center, 8) {
            it == verticallyNear || it == sameLayerFar
        }

        assertEquals(verticallyNear, result)
    }

    @Test
    fun `initial teleport candidate must be safe and inside the shape`() {
        val shape = GeoShape(GeoShapeType.RECTANGLE, mutableListOf(0, 0, 10, 10))
        val inside = net.minecraft.core.BlockPos(5, 64, 5)
        val outside = net.minecraft.core.BlockPos(11, 64, 5)

        assertTrue(shape.isValidTeleportPoint(inside, physicallySafe = true))
        assertFalse(shape.isValidTeleportPoint(outside, physicallySafe = true))
        assertFalse(shape.isValidTeleportPoint(inside, physicallySafe = false))
    }

    @Test
    fun `legacy teleport generation uses constant time representative points`() {
        assertEquals(
            3 to 4,
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(3, 4, 2)).typedGeometry.representativePoint()
        )
        assertEquals(
            -1 to -1,
            GeoShape(
                GeoShapeType.RECTANGLE,
                mutableListOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            ).typedGeometry.representativePoint()
        )
        assertEquals(
            1 to 2,
            GeoShape(GeoShapeType.POLYGON, mutableListOf(1, 2, 5, 2, 1, 6))
                .typedGeometry.representativePoint()
        )
        assertEquals(null, GeoShape(GeoShapeType.UNKNOWN, mutableListOf()).typedGeometry.representativePoint())
    }

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
    @Suppress("DEPRECATION")
    fun `legacy setters cannot leave a partially invalid geometry`() {
        val shape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 10))

        shape.geoShapeType = GeoShapeType.CIRCLE
        shape.shapeParameter = mutableListOf(0, 0, 10)

        assertFails { shape.geoShapeType = GeoShapeType.RECTANGLE }
        assertFails { shape.shapeParameter = mutableListOf(0, 0, 20) }
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

    @Test
    fun `polygon accepts 256 vertices and rejects 257`() {
        val supported = squarePolygonParameters()
        val vertices = supported.chunked(2).map { GeoPoint(it[0], it[1]) }

        assertEquals(256, supported.size / 2)
        val shape = GeoShape(GeoShapeType.POLYGON, supported)
        assertEquals(256, assertIs<BoundedShapeGeometry>(shape.typedGeometry).containmentWorkUnits)
        assertEquals(supported, GeoShape.polygon(vertices).shapeParameter)
        assertFailsWith<IllegalArgumentException> {
            GeoShape(GeoShapeType.POLYGON, MutableList(514) { it })
        }
        @Suppress("DEPRECATION")
        assertFailsWith<IllegalArgumentException> {
            shape.shapeParameter = MutableList(514) { it }
        }
        assertEquals(supported, shape.shapeParameter)
    }

    @Test
    fun `area formatting uses dot decimal separator regardless of default locale`() {
        val savedLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val circle = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 7))
            val info = circle.getShapeInfo()?.string ?: error("getShapeInfo returned null")
            assertTrue(Regex("""\d+\.\d+""").containsMatchIn(info), "Area should contain dot decimal: $info")
            assertFalse(Regex("""\d+,\d+""").containsMatchIn(info), "Area should not use comma decimal: $info")
        } finally {
            java.util.Locale.setDefault(savedLocale)
        }
    }

    private fun squarePolygonParameters(): MutableList<Int> = buildList {
        for (x in 0 until 128 step 2) addAll(listOf(x, 0))
        for (z in 0 until 128 step 2) addAll(listOf(128, z))
        for (x in 128 downTo 2 step 2) addAll(listOf(x, 128))
        for (z in 128 downTo 2 step 2) addAll(listOf(0, z))
    }.toMutableList()
}
