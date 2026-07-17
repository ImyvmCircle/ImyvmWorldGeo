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

class MergeRegionApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `persistence failure restores both aggregates before database membership`() {
        val sourceScope = scope("shared", 7, 0)
        val targetScope = scope("shared", 8, 0)
        val source = Region("source", 7, mutableListOf(sourceScope))
        val target = Region("target", 8, mutableListOf(targetScope))
        bind(source, target)

        val result = mergeRegions(source, target, 100) {
            assertEquals(listOf(target), RegionDatabase.getRegionList())
            assertEquals(emptyList(), source.scopes)
            assertEquals(2, target.scopes.size)
            false
        }

        assertIs<RegionMergeResult.PersistenceFailed>(result)
        assertEquals(listOf(source, target), RegionDatabase.getRegionList())
        assertEquals(listOf(sourceScope), source.scopes)
        assertEquals("shared", sourceScope.scopeName)
        assertEquals(listOf(targetScope), target.scopes)
        assertEquals(emptyMap(), target.ownershipHistorySnapshot())
    }

    @Test
    fun `successful merge reports counts records ownership and retires source`() {
        val sourceScope = scope("shared", 7, 0)
        val targetScope = scope("shared", 8, 0)
        val source = Region("source", 7, mutableListOf(sourceScope))
        val target = Region("target", 8, mutableListOf(targetScope))
        bind(source, target)

        val result = assertIs<RegionMergeResult.Success>(mergeRegions(source, target, 100) { true })

        assertEquals(1, result.scopeCount)
        assertEquals(1, result.renamedCount)
        assertEquals(listOf(target), RegionDatabase.getRegionList())
        assertSame(sourceScope, target.scopes.last())
        assertEquals("shared1", sourceScope.scopeName)
        assertEquals(8, target.ownershipHistory(sourceScope.requireAssignedScopeId()).single().toRegionNumberId)
    }

    @Test
    fun `detached merge source is rejected before persistence`() {
        val source = Region("source", 7, mutableListOf(scope("source", 7, 0)))
        val target = Region("target", 8, mutableListOf(scope("target", 8, 0)))
        bind(source, target)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            mergeRegions(
                Region("source", 7, mutableListOf(scope("source", 7, 0))),
                target,
                100
            ) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertEquals(listOf(source, target), RegionDatabase.getRegionList())
    }

    private fun bind(vararg regions: Region) {
        val directory = Files.createTempDirectory("iwg-merge-test")
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
