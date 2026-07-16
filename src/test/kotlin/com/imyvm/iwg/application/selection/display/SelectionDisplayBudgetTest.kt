package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PolygonGeometry
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectionDisplayBudgetTest {
    @Test
    fun `session never spends pillar reservations on surface work`() {
        val session = SelectionDisplaySession(10)

        assertTrue(session.queuePillar(1, 2))
        assertFalse(session.queuePillar(1, 2))
        assertEquals(9, session.remainingUnits)
        assertEquals(8, session.surfaceUnits)
        assertFalse(session.tryUseSurface(9))
        assertTrue(session.tryUseSurface(8))
        assertEquals(1, session.remainingUnits)
        assertTrue(session.tryUse())
        assertTrue(session.exhausted)
    }

    @Test
    fun `discarded unloaded pillar still consumes its candidate unit`() {
        val session = SelectionDisplaySession(4)

        assertTrue(session.queuePillar(1, 2))
        session.removePillar(1, 2)

        assertEquals(3, session.remainingUnits)
        assertEquals(3, session.surfaceUnits)
        assertEquals(0, session.pillarCount)
    }

    @Test
    fun `extreme line coordinates are sampled across the whole segment without overflow`() {
        val samples = mutableListOf<Pair<Int, Int>>()

        val count = sampleLineCoordinates(
            Int.MIN_VALUE,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            step = 1,
            maxSamples = 5
        ) { x, z -> samples += x to z }

        assertEquals(5, count)
        assertEquals(Int.MIN_VALUE to Int.MIN_VALUE, samples.first())
        assertEquals(Int.MAX_VALUE to Int.MAX_VALUE, samples.last())
        assertTrue(samples.zipWithNext().all { (first, second) -> first.first <= second.first })

        val midpoint = mutableListOf<Pair<Int, Int>>()
        sampleLineCoordinates(Int.MIN_VALUE, 0, Int.MAX_VALUE, 0, 1, 1) { x, z -> midpoint += x to z }
        assertEquals(listOf(0 to 0), midpoint)
    }

    @Test
    fun `large circle sampling is bounded and streamed around the full outline`() {
        val samples = mutableListOf<Pair<Int, Int>>()

        val count = sampleCircleCoordinates(0, 0, Int.MAX_VALUE, step = 1, maxSamples = 8) { x, z ->
            samples += x to z
        }

        assertEquals(8, count)
        assertEquals(Int.MAX_VALUE to 0, samples.first())
        assertEquals(8, samples.distinct().size)
    }

    @Test
    fun `bounded pillar sampling uses the configured spacing and spans full height`() {
        val samples = mutableListOf<Int>()

        val count = sampleVerticalCoordinates(-64, 320, step = 8, maxSamples = 3) { samples += it }

        assertEquals(3, count)
        assertEquals(listOf(-64, 128, 320), samples)
    }

    @Test
    fun `closed boundary edge selection spans a large compact polygon`() {
        val indices = (0 until 4).map { evenlySpacedIndex(it, 4, 50_000) }

        assertEquals(0, indices.first())
        assertEquals(49_999, indices.last())
        assertEquals(4, indices.distinct().size)

        val shape = GeoShape(
            GeoShapeType.POLYGON,
            mutableListOf(0, 0, 100, 0, 100, 100, 0, 100)
        )
        val geometry = assertIs<PolygonGeometry>(shape.typedGeometry)
        assertEquals(4, geometry.vertexCount)
        assertEquals(100, geometry.x(2))
    }

    @Test
    fun `scope traversal uses only the budget left by selection work`() {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null
        )
        val session = SelectionDisplaySession(5)
        assertTrue(session.tryUseSurface(2))
        var displayed = 0

        displayScopeCandidates(List(10) { scope }, session) { displayed++ }

        assertEquals(3, displayed)
        assertTrue(session.exhausted)
    }
}
