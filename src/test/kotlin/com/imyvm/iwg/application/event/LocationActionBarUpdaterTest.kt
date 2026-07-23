package com.imyvm.iwg.application.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationActionBarUpdaterTest {
    @Test
    fun `includes subspace when present`() {
        val text = buildLocationActionBarText("region", "scope", "subspace")
        val parts = text.split(" | ")

        assertEquals(3, parts.size)
        assertTrue(parts[0].contains("region"))
        assertTrue(parts[1].contains("scope"))
        assertTrue(parts[2].contains("subspace"))
    }

    @Test
    fun `omits missing scope and subspace`() {
        val text = buildLocationActionBarText("region", null as String?, null as String?)
        val parts = text.split(" | ")

        assertEquals(1, parts.size)
        assertTrue(parts.single().contains("region"))
    }

    @Test
    fun `keeps wilderness text as the only segment when location is empty`() {
        val text = buildLocationActionBarText(null as String?, null as String?, null as String?)

        assertEquals(1, text.split(" | ").size)
        assertTrue(text.isNotBlank())
    }
}
