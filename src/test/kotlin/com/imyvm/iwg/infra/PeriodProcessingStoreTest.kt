package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.NaturalPeriodKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PeriodProcessingStoreTest {
    @AfterTest
    fun tearDown() {
        PeriodProcessingStore.unbindSession()
    }

    @Test
    fun `round trips processed period ids`() = withTempDirectory { directory ->
        val ids = mapOf(
            NaturalPeriodKind.HOUR to "2026-07-21T00",
            NaturalPeriodKind.DAY to "2026-07-21",
            NaturalPeriodKind.WEEK to "2026-W30",
            NaturalPeriodKind.MONTH to "2026-07"
        )

        PeriodProcessingStore.bindSession(directory)
        PeriodProcessingStore.replaceProcessedPeriodIds(ids)
        PeriodProcessingStore.unbindSession()

        PeriodProcessingStore.bindSession(directory)
        assertEquals(ids, PeriodProcessingStore.getProcessedPeriodIds())
    }

    @Test
    fun `rejects malformed period file`() = withTempDirectory { directory ->
        Files.writeString(directory.resolve("iwg_periods.json"), "[]")

        assertFailsWith<IOException> { PeriodProcessingStore.bindSession(directory) }
    }

    @Test
    fun `writer rejects blank ids without replacing existing file`() = withTempDirectory { directory ->
        val path = directory.resolve("iwg_periods.json")
        PeriodProcessingStore.writeProcessedPeriodIds(path, mapOf(NaturalPeriodKind.HOUR to "2026-07-21T00"))
        val original = Files.readString(path)

        assertFailsWith<IllegalArgumentException> {
            PeriodProcessingStore.writeProcessedPeriodIds(path, mapOf(NaturalPeriodKind.HOUR to ""))
        }
        assertEquals(original, Files.readString(path))
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-period-store-test")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
