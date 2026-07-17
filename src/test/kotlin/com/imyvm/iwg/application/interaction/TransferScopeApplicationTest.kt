package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class TransferScopeApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `persistence failure restores scope order name and ownership histories`() {
        val retained = scope("retained", 7, 0)
        val transferred = scope("shared", 7, 1)
        val source = Region("source", 7, mutableListOf(retained, transferred))
        val target = Region("target", 8, mutableListOf(scope("shared", 8, 0)))
        bind(source, target)

        val result = transferScope(source, transferred, target, 100) { false }

        assertIs<ScopeTransferResult.PersistenceFailed>(result)
        assertEquals(listOf(retained, transferred), source.scopes)
        assertEquals("shared", transferred.scopeName)
        assertEquals(1, target.scopes.size)
        assertEquals(emptyMap(), source.ownershipHistorySnapshot())
        assertEquals(emptyMap(), target.ownershipHistorySnapshot())
    }

    @Test
    fun `successful transfer resolves duplicate name and records new owner`() {
        val retained = scope("retained", 7, 0)
        val transferred = scope("shared", 7, 1)
        val source = Region("source", 7, mutableListOf(retained, transferred))
        val target = Region("target", 8, mutableListOf(scope("shared", 8, 0)))
        bind(source, target)

        val result = assertIs<ScopeTransferResult.Success>(
            transferScope(source, transferred, target, 100) { true }
        )

        assertEquals("shared", result.originalName)
        assertEquals("shared1", result.resolvedName)
        assertSame(retained, source.scopes.single())
        assertSame(transferred, target.scopes.last())
        assertEquals(8, target.ownershipHistory(transferred.requireAssignedScopeId()).single().toRegionNumberId)
    }

    @Test
    fun `detached transfer target is rejected before persistence`() {
        val retained = scope("retained", 7, 0)
        val transferred = scope("moved", 7, 1)
        val source = Region("source", 7, mutableListOf(retained, transferred))
        val target = Region("target", 8, mutableListOf(scope("target", 8, 0)))
        bind(source, target)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            transferScope(source, transferred, Region("target", 8, mutableListOf(scope("target", 8, 0))), 100) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertEquals(listOf(retained, transferred), source.scopes)
    }

    private fun bind(vararg regions: Region) {
        val directory = Files.createTempDirectory("iwg-transfer-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
        regions.forEach(RegionDatabase::insertRegion)
    }

    private fun scope(name: String, regionId: Int, index: Int): GeoScope = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )
}
