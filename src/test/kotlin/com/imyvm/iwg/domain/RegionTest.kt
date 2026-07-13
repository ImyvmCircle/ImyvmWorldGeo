package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RegionTest {
    @Test
    fun `region identity cannot be replaced`() {
        val region = Region("region", 7, mutableListOf(scope("main", 1)))

        assertFailsWith<IllegalArgumentException> { region.numberID = 8 }
        assertEquals(7, region.numberID)
    }

    @Test
    fun `ownership history rejects an unassigned scope id`() {
        assertFailsWith<IllegalArgumentException> {
            ScopeOwnershipEntry(0, 6, 7, 10)
        }
    }

    @Test
    fun `legacy scope and ownership collections are detached snapshots`() {
        val scope = scope("main", 1)
        val region = Region("region", 7, mutableListOf(scope))
        region.recordScopeOwnership(ScopeOwnershipEntry(scope.scopeId.raw, 6, 7, 10))

        region.geometryScope.clear()
        region.ownershipHistoryByScope.getValue(scope.scopeId.raw).clear()

        assertEquals(listOf(scope), region.scopes)
        assertEquals(1, region.ownershipHistory(scope.requireAssignedScopeId()).size)
    }

    @Test
    fun `region rejects duplicate scope names and assigned ids`() {
        val region = Region("region", 7, mutableListOf(scope("main", 1)))

        assertFailsWith<IllegalArgumentException> { region.addScope(scope("MAIN", 2)) }
        assertFailsWith<IllegalArgumentException> { region.addScope(scope("other", 1)) }
    }

    @Test
    fun `missing scope reports structured target without translating in the domain`() {
        val region = Region("region", 7, mutableListOf(scope("main", 1)))

        val error = assertFailsWith<ScopeNotFoundException> { region.getScopeByName("missing") }

        assertIs<IllegalArgumentException>(error)
        assertEquals("missing", error.scopeName)
        assertEquals("region", error.regionName)
        assertEquals("region.error.no_scope", error.message)
    }

    @Test
    fun `scope lookup is case insensitive`() {
        val main = scope("Main", 1)
        val region = Region("region", 7, mutableListOf(main))

        assertEquals(main, region.getScopeByName("mAiN"))
    }

    @Test
    fun `removed scope can be restored at its original position`() {
        val first = scope("first", 1)
        val second = scope("second", 2)
        val region = Region("region", 7, mutableListOf(first, second))

        val index = region.removeScope(first)
        region.restoreScope(index, first)

        assertEquals(listOf(first, second), region.scopes)
    }

    private fun scope(name: String, id: Long) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(com.imyvm.iwg.domain.component.generateCompatScopeIdRaw(7, id.toInt()))
    )
}
