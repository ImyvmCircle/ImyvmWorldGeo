package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegionTest {
    @Test
    fun `legacy scope and ownership collections are detached snapshots`() {
        val scope = scope("main", 1)
        val region = Region("region", 7, mutableListOf(scope))
        region.recordScopeOwnership(ScopeOwnershipEntry(1, 6, 7, 10))

        region.geometryScope.clear()
        region.ownershipHistoryByScope.getValue(1).clear()

        assertEquals(listOf(scope), region.scopes)
        assertEquals(1, region.ownershipHistory(1).size)
    }

    @Test
    fun `region rejects duplicate scope names and assigned ids`() {
        val region = Region("region", 7, mutableListOf(scope("main", 1)))

        assertFailsWith<IllegalArgumentException> { region.addScope(scope("MAIN", 2)) }
        assertFailsWith<IllegalArgumentException> { region.addScope(scope("other", 1)) }
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
        scopeId = ScopeId(id)
    )
}
