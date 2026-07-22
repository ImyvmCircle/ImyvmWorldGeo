package com.imyvm.iwg.application.event

import com.imyvm.iwg.ImyvmWorldGeo
import kotlin.concurrent.thread

internal class AsyncCallbackDispatcher<T>(
    private val label: String,
    private val queueCapacity: () -> Int
) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private val callbacks = mutableListOf<(T) -> Unit>()
    private val queue = ArrayDeque<T>()
    private var processing = false
    private var warnedFull = false

    init {
        thread(isDaemon = true, name = "iwg-$label-dispatch") {
            workerLoop()
        }
    }

    fun registerCallback(callback: (T) -> Unit) {
        synchronized(lock) {
            callbacks.add(callback)
        }
    }

    fun dispatch(payload: T) {
        synchronized(lock) {
            if (queue.size >= queueCapacity()) {
                if (!warnedFull) {
                    warnedFull = true
                    ImyvmWorldGeo.logger.warn("Dropped $label callback payload because the async queue is full.")
                }
                return@synchronized
            }
            warnedFull = false
            queue.addLast(payload)
            lock.notifyAll()
        }
    }

    internal fun awaitIdleForTest(timeoutMillis: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        synchronized(lock) {
            while (processing || queue.isNotEmpty()) {
                val remaining = deadline - System.currentTimeMillis()
                require(remaining > 0L) { "$label dispatcher did not become idle within $timeoutMillis ms" }
                lock.wait(remaining)
            }
        }
    }

    internal fun awaitProcessingForTest(timeoutMillis: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        synchronized(lock) {
            while (!processing) {
                val remaining = deadline - System.currentTimeMillis()
                require(remaining > 0L) { "$label dispatcher did not start processing within $timeoutMillis ms" }
                lock.wait(remaining)
            }
        }
    }

    internal fun clearForTest() {
        synchronized(lock) {
            callbacks.clear()
            queue.clear()
            processing = false
            warnedFull = false
            lock.notifyAll()
        }
    }

    private fun workerLoop() {
        while (true) {
            val next = synchronized(lock) {
                while (queue.isEmpty()) {
                    lock.wait()
                }
                processing = true
                lock.notifyAll()
                queue.removeFirst()
            }
            val snapshot = synchronized(lock) { callbacks.toList() }
            snapshot.forEach { callback ->
                runCatching { callback(next) }.onFailure {
                    ImyvmWorldGeo.logger.warn("$label subscriber threw: ${it.message}", it)
                }
            }
            synchronized(lock) {
                processing = false
                if (queue.isEmpty()) {
                    lock.notifyAll()
                }
            }
        }
    }
}
