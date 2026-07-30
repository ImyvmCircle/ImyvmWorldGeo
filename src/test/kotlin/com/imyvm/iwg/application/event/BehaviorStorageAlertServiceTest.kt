package com.imyvm.iwg.application.event

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BehaviorStorageAlertServiceTest {
    @Test
    fun `online operator alerts are rate limited`() {
        assertTrue(alertDue(1_000L, Long.MIN_VALUE, 60_000L))
        assertFalse(alertDue(60_999L, 1_000L, 60_000L))
        assertTrue(alertDue(61_000L, 1_000L, 60_000L))
        assertTrue(alertDue(500L, 1_000L, 60_000L))
    }
}
