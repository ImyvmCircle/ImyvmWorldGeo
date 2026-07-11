package com.imyvm.iwg.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LazyTickerTest {
    @Test
    fun `interval rejects zero and uses long multiplication`() {
        assertFailsWith<IllegalArgumentException> { lazyIntervalTicks(0) }
        assertEquals(Int.MAX_VALUE.toLong() * 20L, lazyIntervalTicks(Int.MAX_VALUE))
    }

    @Test
    fun `task failure does not prevent later tasks`() {
        val calls = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val tasks = listOf<(Unit) -> Unit>(
            { calls += 1 },
            { error("boom") },
            { calls += 3 }
        )

        runIsolated(tasks, Unit, failures::add)

        assertEquals(listOf(1, 3), calls)
        assertEquals(1, failures.size)
    }
}
