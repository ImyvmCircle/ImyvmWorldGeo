package com.imyvm.iwg.application.event

import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncCallbackDispatcherTest {
    @Test
    fun `dispatcher delivers callbacks asynchronously`() {
        val dispatcher = AsyncCallbackDispatcher<Int>("test-dispatch", { 4 })
        val received = mutableListOf<Int>()
        dispatcher.registerCallback { received.add(it) }

        dispatcher.dispatch(1)
        dispatcher.awaitIdleForTest()

        assertEquals(listOf(1), received)
    }

    @Test
    fun `dispatcher drops newest payload when the queue is full`() {
        val dispatcher = AsyncCallbackDispatcher<Int>("test-overflow", { 1 })
        val received = mutableListOf<Int>()
        dispatcher.registerCallback {
            received.add(it)
            Thread.sleep(150)
        }

        dispatcher.dispatch(1)
        dispatcher.awaitProcessingForTest()
        dispatcher.dispatch(2)
        dispatcher.dispatch(3)
        dispatcher.awaitIdleForTest()

        assertEquals(listOf(1, 2), received)
    }
}
