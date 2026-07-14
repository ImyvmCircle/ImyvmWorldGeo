package com.imyvm.iwg.application.interaction.scope

import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScopeShapeReplacementTest {
    private val worldId = Identifier.parse("minecraft:overworld")

    @Test
    fun `replacement excludes its own scope and persists the new shape`() {
        val oldShape = rectangle(0, 0, 100, 100)
        val newShape = rectangle(10, 10, 110, 110)
        val scope = scope("scope", oldShape)
        val region = Region("region", 1, mutableListOf(scope))
        var saved = false

        val result = replaceScopeShape(region, scope, newShape, listOf(region)) {
            saved = true
            true
        }

        assertEquals(ScopeShapeReplacementResult.Success, result)
        assertTrue(saved)
        assertSame(newShape, scope.geoShape)
    }

    @Test
    fun `persistence failure restores the exact old shape`() {
        val oldShape = rectangle(0, 0, 100, 100)
        val scope = scope("scope", oldShape)
        val region = Region("region", 1, mutableListOf(scope))
        val failedShape = rectangle(10, 10, 110, 110)

        val result = replaceScopeShape(
            region,
            scope,
            failedShape,
            listOf(region)
        ) { false }

        assertEquals(ScopeShapeReplacementResult.PersistenceFailed, result)
        assertSame(oldShape, scope.geoShape)
        assertFailsWith<IOException> {
            replaceScopeGeometryAndSave(scope, failedShape) { throw IOException("save failed") }
        }
        assertSame(oldShape, scope.geoShape)
    }

    @Test
    fun `size and intersection failures do not mutate or save`() {
        val oldShape = rectangle(0, 0, 100, 100)
        val scope = scope("scope", oldShape)
        val other = scope("other", rectangle(200, 200, 300, 300), 2)
        val region = Region("region", 1, mutableListOf(scope, other))
        var saved = false

        val underSize = replaceScopeShape(
            region,
            scope,
            rectangle(0, 0, 1, 1),
            listOf(region)
        ) {
            saved = true
            true
        }
        val intersection = replaceScopeShape(
            region,
            scope,
            rectangle(250, 250, 350, 350),
            listOf(region)
        ) {
            saved = true
            true
        }

        assertEquals(ScopeShapeReplacementResult.Rejected(CreationError.UnderSizeLimit), underSize)
        assertTrue(
            intersection is ScopeShapeReplacementResult.Rejected &&
                intersection.error is CreationError.IntersectionBetweenScopes
        )
        assertFalse(saved)
        assertSame(oldShape, scope.geoShape)
    }

    @Test
    fun `replacement requires canonical ownership and the same shape type`() {
        val oldShape = rectangle(0, 0, 100, 100)
        val scope = scope("scope", oldShape)
        val region = Region("region", 1, mutableListOf(scope))
        val detached = scope("scope", oldShape)
        val detachedRegion = Region("region", 1, mutableListOf(detached))
        val wrongOwnerScope = scope("other", oldShape, 2)
        val wrongOwner = Region("other", 2, mutableListOf(wrongOwnerScope))

        assertFailsWith<IllegalArgumentException> {
            replaceScopeShape(region, detached, rectangle(10, 10, 110, 110), listOf(region)) { true }
        }
        assertFailsWith<IllegalArgumentException> {
            replaceScopeShape(detachedRegion, detached, rectangle(10, 10, 110, 110), listOf(region)) { true }
        }
        assertFailsWith<IllegalArgumentException> {
            replaceScopeShape(
                region,
                wrongOwnerScope,
                rectangle(10, 10, 110, 110),
                listOf(region, wrongOwner)
            ) { true }
        }
        assertFailsWith<IllegalArgumentException> {
            replaceScopeShape(region, scope, GeoShape.circle(GeoPoint(0, 0), 100), listOf(region)) { true }
        }
        assertSame(oldShape, scope.geoShape)
    }

    private fun rectangle(west: Int, north: Int, east: Int, south: Int): GeoShape =
        GeoShape.rectangle(GeoPoint(west, north), GeoPoint(east, south))

    private fun scope(name: String, shape: GeoShape, localId: Int = 1): GeoScope =
        GeoScope(
            name,
            worldId,
            null,
            geoShape = shape,
            scopeId = ScopeId(generateCompatScopeIdRaw(1, localId))
        )
}
