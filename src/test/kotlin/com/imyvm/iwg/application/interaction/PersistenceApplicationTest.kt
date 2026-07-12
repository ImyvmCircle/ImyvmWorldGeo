package com.imyvm.iwg.application.interaction

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse

class PersistenceApplicationTest {
    @Test
    fun `save failure is reported instead of escaping`() {
        assertFalse(saveRegionData { throw IOException("simulated failure") })
    }
}
