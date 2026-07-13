package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.infra.RegionDatabase
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class PersistenceApplicationTest {
    @Test
    fun `save failure is reported instead of escaping`() {
        assertFalse(saveRegionData { throw IOException("simulated failure") })
    }

    @Test
    fun `multi file save failure restores every previous file`() {
        val directory = Files.createTempDirectory("iwg-save-rollback-test")
        try {
            val existing = directory.resolve("existing.db")
            val absent = directory.resolve("absent.json")
            Files.writeString(existing, "old")

            assertFailsWith<IOException> {
                RegionDatabase.withFileRollback(listOf(existing, absent)) {
                    RegionDatabase.atomicWrite(existing) { it.write("new".toByteArray()) }
                    RegionDatabase.atomicWrite(absent) { it.write("created".toByteArray()) }
                    throw IOException("simulated failure")
                }
            }

            assertEquals("old", Files.readString(existing))
            assertFalse(Files.exists(absent))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
