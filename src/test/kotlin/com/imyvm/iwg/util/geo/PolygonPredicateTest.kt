package com.imyvm.iwg.util.geo

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoPoint
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

    @Test
    fun `circle overlap remains exact at int scale`() {
        val maximum = Int.MAX_VALUE
        val circle = GeoShape.circle(GeoPoint(0, 0), maximum)
        val touching = scope("touching", GeoShape.circle(GeoPoint(maximum, 0), 0))
        val separated = scope("separated", GeoShape.circle(GeoPoint(maximum, 1), 0))

        assertEquals(
            listOf("touching"),
            checkIntersection(circle, listOf(touching to "region", separated to "region")).map { it.scopeName }
        )
    }

    @Test
    fun `rectangle and polygon circle overlap remain exact at int scale`() {
        val radius = Int.MAX_VALUE - 100
        val circle = GeoShape.circle(GeoPoint(0, 0), radius)
        val touchingRectangle = scope(
            "touching-rectangle",
            GeoShape.rectangle(GeoPoint(radius, 0), GeoPoint(Int.MAX_VALUE, 100))
        )
        val separatedRectangle = scope(
            "separated-rectangle",
            GeoShape.rectangle(GeoPoint(radius, 1), GeoPoint(Int.MAX_VALUE, 100))
        )
        val touchingPolygon = scope(
            "touching-polygon",
            GeoShape.polygon(
                listOf(
                    GeoPoint(radius, -100),
                    GeoPoint(Int.MAX_VALUE, -100),
                    GeoPoint(Int.MAX_VALUE, 100),
                    GeoPoint(radius, 100)
                )
            )
        )
        val separatedPolygon = scope(
            "separated-polygon",
            GeoShape.polygon(
                listOf(
                    GeoPoint(radius, 1),
                    GeoPoint(Int.MAX_VALUE, 1),
                    GeoPoint(Int.MAX_VALUE, 100),
                    GeoPoint(radius, 100)
                )
            )
        )
        val edgeSeparatedPolygon = scope(
            "edge-separated-polygon",
            GeoShape.polygon(
                listOf(
                    GeoPoint(radius + 1, -100),
                    GeoPoint(Int.MAX_VALUE, -100),
                    GeoPoint(Int.MAX_VALUE, 100),
                    GeoPoint(radius + 1, 100)
                )
            )
        )

        assertEquals(
            listOf("touching-rectangle", "touching-polygon"),
            checkIntersection(
                circle,
                listOf(
                    touchingRectangle,
                    separatedRectangle,
                    touchingPolygon,
                    separatedPolygon,
                    edgeSeparatedPolygon
                ).map { it to "region" }
            ).map { it.scopeName }
        )
    }

    private fun shape(type: GeoShapeType, vararg parameters: Int): GeoShape =
        GeoShape(type, parameters.toMutableList())

    private fun scope(name: String, shape: GeoShape): GeoScope =
        GeoScope(name, Identifier.parse("minecraft:overworld"), null, geoShape = shape)
}
