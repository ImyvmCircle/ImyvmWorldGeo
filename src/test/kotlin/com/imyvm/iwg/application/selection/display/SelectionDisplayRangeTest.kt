package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionDisplayRangeTest {
    private val overworld: Identifier = Identifier.parse("minecraft:overworld")
    private val nether: Identifier = Identifier.parse("minecraft:the_nether")

    @Test
    fun `nearby scopes are collected while distant shapeless and other-world scopes are skipped`() {
        val near = rectScope("near", 1, 50, 50, 100, 100)
        val containing = rectScope("containing", 2, -1000, -1000, 1000, 1000)
        val far = rectScope("far", 3, 500, 500, 600, 600)
        val otherWorld = rectScope("netherScope", 4, 0, 0, 10, 10, nether)
        val shapeless = GeoScope("shapeless", overworld, null, geoShape = null, scopeId = scopeId(5))
        val region = Region("region", 7, mutableListOf(near, containing, far, otherWorld, shapeless))

        val (scopes, subSpaces) = collectDisplayCandidates(listOf(region), overworld, BlockPos(0, 0, 0), null, null)

        assertEquals(listOf(near, containing), scopes)
        assertTrue(subSpaces.isEmpty())
    }

    @Test
    fun `scope exactly at the display radius is included while one block beyond is not`() {
        val atBoundary = rectScope("atBoundary", 1, 160, 0, 200, 10)
        val beyond = rectScope("beyond", 2, 161, 0, 200, 10)
        val region = Region("region", 7, mutableListOf(atBoundary, beyond))

        val (scopes, _) = collectDisplayCandidates(listOf(region), overworld, BlockPos(0, 0, 0), null, null)

        assertEquals(listOf(atBoundary), scopes)
    }

    @Test
    fun `circle scopes are measured by their bounding box`() {
        val nearCircle = GeoScope(
            "nearCircle",
            overworld,
            null,
            geoShape = GeoShape.circle(GeoPoint(100, 0), 10),
            scopeId = scopeId(1)
        )
        val farCircle = GeoScope(
            "farCircle",
            overworld,
            null,
            geoShape = GeoShape.circle(GeoPoint(200, 0), 10),
            scopeId = scopeId(2)
        )
        val region = Region("region", 7, mutableListOf(nearCircle, farCircle))

        val (scopes, _) = collectDisplayCandidates(listOf(region), overworld, BlockPos(0, 0, 0), null, null)

        assertEquals(listOf(nearCircle), scopes)
    }

    @Test
    fun `excluded scope and subspace are skipped`() {
        val main = rectScope("main", 1, -100, -100, 100, 100)
        val region = Region("region", 7, mutableListOf(main))
        val plot = SubSpace(
            1,
            "plot",
            main.requireAssignedScopeId(),
            main.worldId,
            GeoShape.rectangle(GeoPoint(1, 1), GeoPoint(2, 2))
        )
        region.addSubSpaceFromOwner(plot)

        val (scopes, subSpaces) = collectDisplayCandidates(listOf(region), overworld, BlockPos(0, 0, 0), main, plot)

        assertTrue(scopes.isEmpty())
        assertTrue(subSpaces.isEmpty())
    }

    @Test
    fun `subspaces are filtered by their own distance to the player`() {
        val main = rectScope("main", 1, -2000, -2000, 2000, 2000)
        val region = Region("region", 7, mutableListOf(main))
        val nearPlot = SubSpace(
            1,
            "nearPlot",
            main.requireAssignedScopeId(),
            main.worldId,
            GeoShape.rectangle(GeoPoint(10, 10), GeoPoint(20, 20))
        )
        val farPlot = SubSpace(
            2,
            "farPlot",
            main.requireAssignedScopeId(),
            main.worldId,
            GeoShape.rectangle(GeoPoint(500, 500), GeoPoint(600, 600))
        )
        region.addSubSpaceFromOwner(nearPlot)
        region.addSubSpaceFromOwner(farPlot)

        val (scopes, subSpaces) = collectDisplayCandidates(listOf(region), overworld, BlockPos(0, 0, 0), null, null)

        assertEquals(listOf(main), scopes)
        assertEquals(listOf(nearPlot), subSpaces)
    }

    private fun rectScope(
        name: String,
        id: Int,
        west: Int,
        north: Int,
        east: Int,
        south: Int,
        world: Identifier = overworld
    ) = GeoScope(
        name,
        world,
        null,
        geoShape = GeoShape.rectangle(GeoPoint(west, north), GeoPoint(east, south)),
        scopeId = scopeId(id)
    )

    private fun scopeId(id: Int) = ScopeId(generateCompatScopeIdRaw(7, id))
}
