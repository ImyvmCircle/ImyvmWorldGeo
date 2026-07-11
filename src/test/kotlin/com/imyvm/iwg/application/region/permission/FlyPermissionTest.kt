package com.imyvm.iwg.application.region.permission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlyPermissionTest {
    @Test
    fun `deadline is absolute and independent of lazy interval`() {
        assertEquals(1_100L, tickDeadline(1_000L, 5))
        assertEquals(1_000L, tickDeadline(1_000L, 0))
        assertFailsWith<IllegalArgumentException> { tickDeadline(1_000L, -1) }
    }
}
