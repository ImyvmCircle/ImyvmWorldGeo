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

class RenameApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `region rename restores its name when persistence fails`() {
        val region = bind(region("before", 7))

        assertFalse(renameRegion(region, "after") { false })

        assertEquals("before", region.name)
    }

    @Test
    fun `scope rename restores its name when persistence fails`() {
        val region = bind(region("region", 7))
        val scope = region.scopes.single()

        assertFalse(renameScope(region, scope, "after") { false })

        assertEquals("main", scope.scopeName)
    }

    @Test
    fun `rename rejects detached targets before persistence`() {
        val canonical = bind(region("canonical", 7))
        val detached = region("canonical", 7)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            renameRegion(detached, "other") {
                saveCalls++
                true
            }
        }
        assertFailsWith<IllegalArgumentException> {
            renameScope(canonical, scope("main", 7), "other") {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertEquals("canonical", canonical.name)
        assertEquals("main", canonical.scopes.single().scopeName)
    }

    private fun bind(region: Region): Region {
        val directory = Files.createTempDirectory("iwg-rename-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
        RegionDatabase.insertRegion(region)
        return region
    }

    private fun region(name: String, id: Int): Region = Region(name, id, mutableListOf(scope("main", id)))

    private fun scope(name: String, regionId: Int): GeoScope = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, 0))
    )
}
