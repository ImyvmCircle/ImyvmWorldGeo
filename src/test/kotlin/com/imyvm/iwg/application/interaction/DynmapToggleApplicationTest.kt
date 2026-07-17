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
import kotlin.test.assertTrue

class DynmapToggleApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `region visibility rolls back when persistence fails`() {
        val region = bind(region("region", 7))

        assertFalse(toggleRegionDynmap(region) { false })

        assertTrue(region.showOnDynmap)
    }

    @Test
    fun `scope visibility commits after persistence succeeds`() {
        val region = bind(region("region", 7))
        val scope = region.scopes.single()

        assertTrue(toggleScopeDynmap(region, scope) { true })

        assertFalse(scope.showOnDynmap)
    }

    @Test
    fun `detached region is rejected before persistence`() {
        bind(region("canonical", 7))
        val detached = region("canonical", 7)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            toggleRegionDynmap(detached) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertTrue(detached.showOnDynmap)
    }

    @Test
    fun `detached scope and detached owner pair are rejected before persistence`() {
        val canonical = bind(region("canonical", 7))
        val detachedScope = scope("main", 7)
        val detachedOwner = Region("canonical", 7, mutableListOf(detachedScope))
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            toggleScopeDynmap(canonical, scope("main", 7)) {
                saveCalls++
                true
            }
        }
        assertFailsWith<IllegalArgumentException> {
            toggleScopeDynmap(detachedOwner, detachedScope) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertTrue(canonical.scopes.single().showOnDynmap)
        assertTrue(detachedScope.showOnDynmap)
    }

    private fun bind(region: Region): Region {
        val directory = Files.createTempDirectory("iwg-dynmap-toggle-test")
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
