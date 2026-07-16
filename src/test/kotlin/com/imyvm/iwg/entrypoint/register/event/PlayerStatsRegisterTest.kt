package com.imyvm.iwg.inter.register.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerStatsRegisterTest {
    @Test
    fun `snapshot time advances only after a due successful save`() {
        var saveCalls = 0
        val notDue = updatePlayerStatsSnapshotTime(1_000L, 1_999L, 1_000L) {
            saveCalls++
            true
        }
        assertEquals(1_000L, notDue)
        assertEquals(0, saveCalls)

        val saved = updatePlayerStatsSnapshotTime(notDue, 2_000L, 1_000L) {
            saveCalls++
            true
        }
        assertEquals(2_000L, saved)
        assertEquals(1, saveCalls)
    }

    @Test
    fun `failed snapshot remains due and retries on the next tick`() {
        var saveCalls = 0
        val failedAt = updatePlayerStatsSnapshotTime(1_000L, 2_000L, 1_000L) {
            saveCalls++
            false
        }
        assertEquals(1_000L, failedAt)

        val retriedAt = updatePlayerStatsSnapshotTime(failedAt, 2_001L, 1_000L) {
            saveCalls++
            true
        }
        assertEquals(2_001L, retriedAt)
        assertEquals(2, saveCalls)
    }

    @Test
    fun `clock rollback does not trigger a snapshot`() {
        var saveCalls = 0
        val result = updatePlayerStatsSnapshotTime(2_000L, 1_000L, 1_000L) {
            saveCalls++
            true
        }

        assertEquals(2_000L, result)
        assertEquals(0, saveCalls)
    }

    @Test
    fun `snapshot timing rejects invalid inputs`() {
        assertFailsWith<IllegalArgumentException> {
            updatePlayerStatsSnapshotTime(-1L, 0L, 1L) { true }
        }
        assertFailsWith<IllegalArgumentException> {
            updatePlayerStatsSnapshotTime(0L, -1L, 1L) { true }
        }
        assertFailsWith<IllegalArgumentException> {
            updatePlayerStatsSnapshotTime(0L, 0L, 0L) { true }
        }
    }
}
