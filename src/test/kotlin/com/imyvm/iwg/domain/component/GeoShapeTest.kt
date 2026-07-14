package com.imyvm.iwg.domain.component

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GeoShapeTest {
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

    @Test
    fun `polygon accepts 256 vertices and rejects 257`() {
        val supported = squarePolygonParameters()

        assertEquals(256, supported.size / 2)
        val shape = GeoShape(GeoShapeType.POLYGON, supported)
        assertFailsWith<IllegalArgumentException> {
            GeoShape(GeoShapeType.POLYGON, MutableList(514) { it })
        }
        assertFailsWith<IllegalArgumentException> {
            shape.shapeParameter = MutableList(514) { it }
        }
        assertEquals(supported, shape.shapeParameter)
    }

    private fun squarePolygonParameters(): MutableList<Int> = buildList {
        for (x in 0 until 128 step 2) addAll(listOf(x, 0))
        for (z in 0 until 128 step 2) addAll(listOf(128, z))
        for (x in 128 downTo 2 step 2) addAll(listOf(x, 128))
        for (z in 128 downTo 2 step 2) addAll(listOf(0, z))
    }.toMutableList()
}
