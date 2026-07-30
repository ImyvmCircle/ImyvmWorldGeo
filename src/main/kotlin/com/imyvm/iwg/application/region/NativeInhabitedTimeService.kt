package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchRequest
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeBatchResult
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeCompleteness
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeReading
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeSource
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.visitors.CollectFields
import net.minecraft.nbt.visitors.FieldSelector
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.storage.ChunkScanAccess
import java.io.EOFException
import java.io.UTFDataFormatException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.zip.ZipException

internal object NativeInhabitedTimeService {
    const val MAX_CHUNKS = 4_096
    private const val ASYNC_PAGE_SIZE = 128
    private const val SERVER_THREAD_PAGE_SIZE = 128
    private const val MILLIS_PER_TICK = 50L

    fun query(
        server: MinecraftServer,
        request: WorldGeoNativeInhabitedTimeBatchRequest
    ): CompletableFuture<WorldGeoNativeInhabitedTimeBatchResult> {
        val fixed = freezeAndValidate(request)
        val collectedAtMillis = System.currentTimeMillis()
        return capturePages(server, fixed.chunks, 0, linkedMapOf())
            .thenCompose { captured -> readCaptured(fixed, collectedAtMillis, captured) }
    }

    internal fun queryWithReaders(
        request: WorldGeoNativeInhabitedTimeBatchRequest,
        collectedAtMillis: Long,
        loadedReader: (WorldGeoDimensionChunk) -> Long?,
        persistedReader: (WorldGeoDimensionChunk) -> CompletableFuture<PersistedRead>
    ): CompletableFuture<WorldGeoNativeInhabitedTimeBatchResult> {
        val fixed = freezeAndValidate(request)
        val captured = fixed.chunks.associateWith { chunk ->
            loadedReader(chunk)?.let(Captured::Loaded) ?: Captured.Persisted {
                persistedReader(chunk)
            }
        }
        return readCaptured(fixed, collectedAtMillis, captured)
    }

    private fun freezeAndValidate(
        request: WorldGeoNativeInhabitedTimeBatchRequest
    ): WorldGeoNativeInhabitedTimeBatchRequest {
        require(request.inputVersion.isNotBlank()) { "input version must not be blank" }
        val chunks = request.chunks.distinct()
        require(chunks.size <= MAX_CHUNKS) { "chunk count must not exceed $MAX_CHUNKS" }
        return request.copy(chunks = chunks)
    }

    private fun capturePages(
        server: MinecraftServer,
        chunks: List<WorldGeoDimensionChunk>,
        offset: Int,
        captured: MutableMap<WorldGeoDimensionChunk, Captured>
    ): CompletableFuture<Map<WorldGeoDimensionChunk, Captured>> {
        if (offset >= chunks.size) return CompletableFuture.completedFuture(captured)
        val page = chunks.subList(offset, minOf(offset + SERVER_THREAD_PAGE_SIZE, chunks.size))
        return captureNextTick(server, page).thenCompose { pageCaptured ->
            captured.putAll(pageCaptured)
            capturePages(server, chunks, offset + page.size, captured)
        }
    }

    private fun captureNextTick(
        server: MinecraftServer,
        chunks: List<WorldGeoDimensionChunk>
    ): CompletableFuture<Map<WorldGeoDimensionChunk, Captured>> {
        val result = CompletableFuture<Map<WorldGeoDimensionChunk, Captured>>()
        val operation = Runnable {
            try {
                result.complete(capture(server, chunks))
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        server.schedule(TickTask(server.tickCount + 1, operation))
        return result
    }

    private fun capture(
        server: MinecraftServer,
        chunks: List<WorldGeoDimensionChunk>
    ): Map<WorldGeoDimensionChunk, Captured> {
        val captured = linkedMapOf<WorldGeoDimensionChunk, Captured>()
        chunks.groupBy { it.dimensionId }.forEach { (dimensionId, dimensionChunks) ->
            val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId))
            if (level == null) {
                dimensionChunks.forEach {
                    captured[it] = Captured.Failed(WorldGeoNativeInhabitedTimeCompleteness.INDEX_NOT_INITIALIZED)
                }
                return@forEach
            }
            val scanner = try {
                level.chunkSource.chunkScanner()
            } catch (_: IllegalStateException) {
                dimensionChunks.forEach {
                    captured[it] = Captured.Failed(WorldGeoNativeInhabitedTimeCompleteness.INDEX_NOT_INITIALIZED)
                }
                return@forEach
            }
            dimensionChunks.forEach { chunk ->
                val loaded = level.chunkSource.getChunkNow(chunk.chunkX, chunk.chunkZ)
                captured[chunk] = loaded?.let { Captured.Loaded(it.inhabitedTime) }
                    ?: Captured.Persisted { scan(scanner, chunk) }
            }
        }
        return captured
    }

    internal fun scan(
        scanner: ChunkScanAccess,
        chunk: WorldGeoDimensionChunk
    ): CompletableFuture<PersistedRead> {
        val collector = CollectFields(FieldSelector(LongTag.TYPE, "InhabitedTime"))
        val future = try {
            scanner.scanChunk(ChunkPos(chunk.chunkX, chunk.chunkZ), collector)
        } catch (_: IllegalStateException) {
            return CompletableFuture.completedFuture(
                PersistedRead(null, WorldGeoNativeInhabitedTimeCompleteness.INDEX_NOT_INITIALIZED)
            )
        } catch (error: Throwable) {
            return CompletableFuture.completedFuture(PersistedRead(null, classifyFailure(error)))
        }
        return future.handle { _, error ->
            if (error != null) {
                PersistedRead(null, classifyFailure(error))
            } else {
                val result = try {
                    collector.result
                } catch (_: NoSuchElementException) {
                    null
                }
                if (result == null) {
                    PersistedRead(null, WorldGeoNativeInhabitedTimeCompleteness.CHUNK_NOT_FOUND)
                } else {
                    val compound = result as? CompoundTag
                    val ticks = compound?.getLong("InhabitedTime")?.orElse(null)
                    if (ticks == null) {
                        PersistedRead(null, WorldGeoNativeInhabitedTimeCompleteness.CORRUPT_DATA)
                    } else {
                        PersistedRead(ticks, WorldGeoNativeInhabitedTimeCompleteness.COMPLETE)
                    }
                }
            }
        }
    }

    private fun readCaptured(
        request: WorldGeoNativeInhabitedTimeBatchRequest,
        collectedAtMillis: Long,
        captured: Map<WorldGeoDimensionChunk, Captured>
    ): CompletableFuture<WorldGeoNativeInhabitedTimeBatchResult> {
        val readings = linkedMapOf<WorldGeoDimensionChunk, WorldGeoNativeInhabitedTimeReading>()
        val persisted = mutableListOf<Pair<WorldGeoDimensionChunk, Captured.Persisted>>()
        request.chunks.forEach { chunk ->
            when (val value = captured.getValue(chunk)) {
                is Captured.Loaded -> readings[chunk] = reading(
                    chunk,
                    value.ticks,
                    collectedAtMillis,
                    request.inputVersion,
                    WorldGeoNativeInhabitedTimeSource.LOADED
                )
                is Captured.Failed -> readings[chunk] = failedReading(
                    chunk,
                    collectedAtMillis,
                    request.inputVersion,
                    value.completeness
                )
                is Captured.Persisted -> persisted += chunk to value
            }
        }
        return readPages(
            persisted,
            0,
            readings,
            collectedAtMillis,
            request.inputVersion
        ).thenApply {
            WorldGeoNativeInhabitedTimeBatchResult(
                request.chunks.map(readings::getValue),
                request.inputVersion
            )
        }
    }

    private fun readPages(
        persisted: List<Pair<WorldGeoDimensionChunk, Captured.Persisted>>,
        offset: Int,
        readings: MutableMap<WorldGeoDimensionChunk, WorldGeoNativeInhabitedTimeReading>,
        collectedAtMillis: Long,
        inputVersion: String
    ): CompletableFuture<Void> {
        if (offset >= persisted.size) return CompletableFuture.completedFuture(null)
        val page = persisted.subList(offset, minOf(offset + ASYNC_PAGE_SIZE, persisted.size))
        val futures = page.map { (chunk, captured) ->
            try {
                captured.reader().handle { value, error ->
                    val persistedRead = if (error == null) {
                        value
                    } else {
                        PersistedRead(null, classifyFailure(error))
                    }
                    synchronized(readings) {
                        readings[chunk] = if (persistedRead.completeness ==
                            WorldGeoNativeInhabitedTimeCompleteness.COMPLETE
                        ) {
                            reading(
                                chunk,
                                requireNotNull(persistedRead.ticks),
                                collectedAtMillis,
                                inputVersion,
                                WorldGeoNativeInhabitedTimeSource.PERSISTED
                            )
                        } else {
                            failedReading(
                                chunk,
                                collectedAtMillis,
                                inputVersion,
                                persistedRead.completeness
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                synchronized(readings) {
                    readings[chunk] = failedReading(
                        chunk,
                        collectedAtMillis,
                        inputVersion,
                        classifyFailure(error)
                    )
                }
                CompletableFuture.completedFuture(null)
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenCompose {
            readPages(
                persisted,
                offset + page.size,
                readings,
                collectedAtMillis,
                inputVersion
            )
        }
    }

    private fun reading(
        chunk: WorldGeoDimensionChunk,
        ticks: Long,
        collectedAtMillis: Long,
        inputVersion: String,
        source: WorldGeoNativeInhabitedTimeSource
    ): WorldGeoNativeInhabitedTimeReading {
        if (ticks < 0L) {
            return failedReading(
                chunk,
                collectedAtMillis,
                inputVersion,
                WorldGeoNativeInhabitedTimeCompleteness.NEGATIVE_TICKS
            )
        }
        val millis = try {
            Math.multiplyExact(ticks, MILLIS_PER_TICK)
        } catch (_: ArithmeticException) {
            return failedReading(
                chunk,
                collectedAtMillis,
                inputVersion,
                WorldGeoNativeInhabitedTimeCompleteness.MILLIS_OVERFLOW
            )
        }
        return WorldGeoNativeInhabitedTimeReading(
            chunk,
            ticks,
            millis,
            collectedAtMillis,
            inputVersion,
            WorldGeoNativeInhabitedTimeCompleteness.COMPLETE,
            source
        )
    }

    private fun failedReading(
        chunk: WorldGeoDimensionChunk,
        collectedAtMillis: Long,
        inputVersion: String,
        completeness: WorldGeoNativeInhabitedTimeCompleteness
    ): WorldGeoNativeInhabitedTimeReading =
        WorldGeoNativeInhabitedTimeReading(
            chunk,
            null,
            null,
            collectedAtMillis,
            inputVersion,
            completeness,
            null
        )

    private fun classifyFailure(error: Throwable): WorldGeoNativeInhabitedTimeCompleteness {
        var current = if (error is CompletionException) error.cause ?: error else error
        while (current.cause != null && current.cause !== current) {
            if (current is EOFException ||
                current is UTFDataFormatException ||
                current is ZipException ||
                current.javaClass.name.contains("nbt", ignoreCase = true)
            ) {
                return WorldGeoNativeInhabitedTimeCompleteness.CORRUPT_DATA
            }
            current = requireNotNull(current.cause)
        }
        return if (current is EOFException ||
            current is UTFDataFormatException ||
            current is ZipException ||
            current.javaClass.name.contains("nbt", ignoreCase = true)
        ) {
            WorldGeoNativeInhabitedTimeCompleteness.CORRUPT_DATA
        } else {
            WorldGeoNativeInhabitedTimeCompleteness.READ_FAILED
        }
    }

    internal data class PersistedRead(
        val ticks: Long?,
        val completeness: WorldGeoNativeInhabitedTimeCompleteness
    )

    private sealed interface Captured {
        data class Loaded(val ticks: Long) : Captured
        data class Persisted(val reader: () -> CompletableFuture<PersistedRead>) : Captured
        data class Failed(val completeness: WorldGeoNativeInhabitedTimeCompleteness) : Captured
    }
}
