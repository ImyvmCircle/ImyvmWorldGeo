package com.imyvm.iwg.domain.component

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame

class GeoScopeTest {
    @Test
    fun `legacy property setters reject uncontrolled state changes`() {
        val shape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 10))
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            BlockPos.ZERO,
            geoShape = shape
        )

        scope.scopeName = "scope"
        scope.worldId = Identifier.parse("minecraft:overworld")
        scope.teleportPoint = BlockPos.ZERO
        scope.geoShape = shape

        assertFails { scope.scopeName = "renamed" }
        assertFails { scope.worldId = Identifier.parse("minecraft:the_nether") }
        assertFails { scope.teleportPoint = BlockPos(1, 2, 3) }
        assertFails { scope.isTeleportPointPublic = true }
        assertFails { scope.geoShape = null }
        assertFails { scope.showOnDynmap = false }

        assertEquals("scope", scope.scopeName)
        assertEquals(BlockPos.ZERO, scope.teleportPoint)
        assertSame(shape, scope.geoShape)
    }

    @Test
    fun `controlled operations replace complete scope state`() {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null
        )
        val shape = GeoShape(GeoShapeType.RECTANGLE, mutableListOf(0, 0, 10, 10))
        val point = BlockPos(1, 2, 3)

        scope.renameTo("renamed")
        scope.updateTeleportPoint(point)
        scope.setTeleportPointPublic(true)
        scope.replaceGeometry(shape)
        scope.setDynmapVisibility(false)

        assertEquals("renamed", scope.scopeName)
        assertEquals(point, scope.teleportPoint)
        assertEquals(true, scope.isTeleportPointPublic)
        assertSame(shape, scope.geoShape)
        assertEquals(false, scope.showOnDynmap)
    }
}
