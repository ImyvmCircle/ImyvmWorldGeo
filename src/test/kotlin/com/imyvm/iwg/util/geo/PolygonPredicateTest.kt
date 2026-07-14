package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolygonPredicateTest {
    @Test
    fun `orientation preserves a unit determinant at int scale`() {
        assertEquals(
            -1,
            orientationSign(0, 0, Int.MAX_VALUE, Int.MAX_VALUE - 1, Int.MAX_VALUE - 1, Int.MAX_VALUE - 2)
        )
    }

    @Test
    fun `segment intersection distinguishes separation touching and crossing`() {
        assertFalse(
            segmentsIntersect(
                0,
                0,
                Int.MAX_VALUE,
                Int.MAX_VALUE - 1,
                Int.MAX_VALUE - 1,
                Int.MAX_VALUE - 2,
                Int.MAX_VALUE - 2,
                Int.MAX_VALUE - 3
            )
        )
        assertTrue(segmentsIntersect(0, 0, 10, 0, 10, 0, 10, 10))
        assertTrue(segmentsIntersect(0, 0, 10, 10, 0, 10, 10, 0))
    }

    @Test
    fun `legacy containment remains exact at int boundaries`() {
        val vertices = listOf(
            Int.MIN_VALUE to Int.MIN_VALUE,
            Int.MAX_VALUE to Int.MIN_VALUE,
            Int.MAX_VALUE to Int.MAX_VALUE,
            Int.MIN_VALUE to Int.MAX_VALUE
        )

        assertTrue(polygonContainsPoint(0, Int.MIN_VALUE, vertices))
        assertTrue(polygonContainsPoint(0, 0, vertices))
    }

    @Test
    fun `typed overlap dispatch covers polygon rectangle and circle`() {
        val polygon = shape(GeoShapeType.POLYGON, 0, 0, 100, 0, 100, 100, 0, 100)
        val overlappingPolygon = scope(
            "polygon",
            shape(GeoShapeType.POLYGON, 90, 90, 120, 90, 120, 120, 90, 120)
        )
        val overlappingRectangle = scope(
            "rectangle",
            shape(GeoShapeType.RECTANGLE, 95, 10, 110, 20)
        )
        val containedCircle = scope("circle", shape(GeoShapeType.CIRCLE, 50, 50, 10))
        val separate = scope(
            "separate",
            shape(GeoShapeType.POLYGON, 200, 200, 220, 200, 220, 220, 200, 220)
        )

        val intersections = checkIntersection(
            polygon,
            listOf(overlappingPolygon, overlappingRectangle, containedCircle, separate).map { it to "region" }
        )

        assertEquals(listOf("polygon", "rectangle", "circle"), intersections.map { it.scopeName })
        assertEquals(
            1,
            checkIntersection(
                shape(GeoShapeType.RECTANGLE, 90, 90, 110, 110),
                listOf(scope("existing", polygon) to "region")
            ).size
        )
    }

    private fun shape(type: GeoShapeType, vararg parameters: Int): GeoShape =
        GeoShape(type, parameters.toMutableList())

    private fun scope(name: String, shape: GeoShape): GeoScope =
        GeoScope(name, Identifier.parse("minecraft:overworld"), null, geoShape = shape)
}
