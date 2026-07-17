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
import kotlin.test.assertFalse

class CreationPersistenceTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `new region is removed when persistence fails`() {
        bindEmpty()
        val region = region("new", 7)

        assertFalse(persistCreatedRegion(region) { false })

        assertEquals(emptyList(), RegionDatabase.getRegionList())
    }

    @Test
    fun `new scope is removed when persistence fails`() {
        val region = region("region", 7)
        bindEmpty()
        RegionDatabase.insertRegion(region)
        val added = scope("added", 7, 1)

        assertFalse(persistCreatedScope(region, added) { false })

        assertEquals(1, region.scopes.size)
        assertEquals("main", region.scopes.single().scopeName)
    }

    @Test
    fun `scope creation rejects detached owner before persistence`() {
        val canonical = region("canonical", 7)
        bindEmpty()
        RegionDatabase.insertRegion(canonical)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            persistCreatedScope(region("canonical", 7), scope("added", 7, 1)) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertEquals(1, canonical.scopes.size)
    }

    private fun bindEmpty() {
        val directory = Files.createTempDirectory("iwg-creation-persistence-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
    }

    private fun region(name: String, id: Int): Region = Region(name, id, mutableListOf(scope("main", id, 0)))

    private fun scope(name: String, regionId: Int, index: Int): GeoScope = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )
}
