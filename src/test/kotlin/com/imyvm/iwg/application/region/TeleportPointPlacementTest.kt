package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.infra.config.MAX_TELEPORT_FALLBACK_SEARCH_RADIUS
import com.imyvm.iwg.infra.config.requireTeleportFallbackSearchRadius
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeleportPointPlacementTest {
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
        val center = BlockPos.ZERO
        val verticallyNear = center.above()
        val sameLayerFar = center.offset(8, 0, 8)

        val result = findClosestMatchingBlockPos(center, 8) {
            it == verticallyNear || it == sameLayerFar
        }

        assertEquals(verticallyNear, result)
    }

    @Test
    fun `teleport candidate must be safe and inside the shape`() {
        val shape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(10, 10))
        val inside = BlockPos(5, 64, 5)
        val outside = BlockPos(11, 64, 5)

        assertTrue(isValidShapeTeleportPoint(shape, inside, physicallySafe = true))
        assertFalse(isValidShapeTeleportPoint(shape, outside, physicallySafe = true))
        assertFalse(isValidShapeTeleportPoint(shape, inside, physicallySafe = false))
    }
}
