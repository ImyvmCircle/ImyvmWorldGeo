package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.WorldGeoDimensionChunk
import com.imyvm.iwg.domain.WorldGeoNativeInhabitedTimeCompleteness
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StreamTagVisitor
import net.minecraft.resources.Identifier
import net.minecraft.world.level.chunk.storage.ChunkScanAccess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeInhabitedTimeScanTest {
    private val chunk = WorldGeoDimensionChunk(
        Identifier.parse("minecraft:overworld"),
        2,
        -3
    )

    @Test
    fun `native scanner reads only root inhabited time`() {
        val tag = CompoundTag().also { it.putLong("InhabitedTime", 77L) }
        val scanner = ChunkScanAccess { _, visitor ->
            stream(tag, visitor)
            CompletableFuture.completedFuture(null)
        }

        val result = NativeInhabitedTimeService.scan(scanner, chunk).get()

        assertEquals(77L, result.ticks)
        assertEquals(WorldGeoNativeInhabitedTimeCompleteness.COMPLETE, result.completeness)
    }

    @Test
    fun `native scanner distinguishes absent chunk from malformed field`() {
        val absent = ChunkScanAccess { _, _ -> CompletableFuture.completedFuture(null) }
        val malformedTag = CompoundTag().also { it.putString("InhabitedTime", "invalid") }
        val malformed = ChunkScanAccess { _, visitor ->
            stream(malformedTag, visitor)
            CompletableFuture.completedFuture(null)
        }

        val absentResult = NativeInhabitedTimeService.scan(absent, chunk).get()
        val malformedResult = NativeInhabitedTimeService.scan(malformed, chunk).get()

        assertNull(absentResult.ticks)
        assertEquals(
            WorldGeoNativeInhabitedTimeCompleteness.CHUNK_NOT_FOUND,
            absentResult.completeness
        )
        assertNull(malformedResult.ticks)
        assertEquals(
            WorldGeoNativeInhabitedTimeCompleteness.CORRUPT_DATA,
            malformedResult.completeness
        )
    }

    private fun stream(tag: CompoundTag, visitor: StreamTagVisitor) {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { NbtIo.write(tag, it) }
        }.toByteArray()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            NbtIo.parse(input, visitor, NbtAccounter.unlimitedHeap())
        }
    }
}
