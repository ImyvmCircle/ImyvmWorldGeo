package com.imyvm.iwg.application.region

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegionIdHandlerTest {
    @Test
    fun `finds the only available discriminator`() {
        val baseId = (123 shl 11) or (4 shl 7)
        val existingIds = (0 until 128)
            .filterNot { it == 73 }
            .mapTo(mutableSetOf()) { baseId or it }

        assertEquals(
            baseId or 73,
            allocateRegionId(4, 123, existingIds, initialDiscriminator = 72)
        )
    }

    @Test
    fun `fails when every discriminator is occupied`() {
        val baseId = (123 shl 11) or (4 shl 7)
        val existingIds = (0 until 128).mapTo(mutableSetOf()) { baseId or it }

        assertFailsWith<RegionIdCapacityExceededException> {
            allocateRegionId(4, 123, existingIds, initialDiscriminator = 42)
        }
    }
}
