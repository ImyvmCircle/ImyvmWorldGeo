package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
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
    fun `region rejects invalid names and an empty scope list`() {
        assertFailsWith<IllegalArgumentException> { Region("1region", 7, mutableListOf(scope("main", 1))) }
        assertFailsWith<IllegalArgumentException> { Region("region", 7, mutableListOf()) }

        val region = Region("region", 7, mutableListOf(scope("main", 1)))
        assertFailsWith<IllegalArgumentException> { region.name = "region_" }
        assertFailsWith<IllegalStateException> { region.geometryScope = mutableListOf() }
        assertEquals("region", region.name)
        assertEquals(1, region.scopes.size)
    }

    @Test
    fun `legacy settings replacement is rejected without changing canonical state`() {
        val region = Region(
            "region",
            7,
            mutableListOf(scope("main", 1)),
            mutableListOf(PermissionSetting(PermissionKey.BUILD, true))
        )

        assertFailsWith<IllegalStateException> {
            region.settings = mutableListOf(
                PermissionSetting(PermissionKey.PVP, true),
                PermissionSetting(PermissionKey.PVP, false)
            )
        }

        assertEquals(PermissionKey.BUILD, region.settings.single().key)
    }

    @Test
    fun `ownership history rejects an unassigned scope id`() {
        assertFailsWith<IllegalArgumentException> {
            ScopeOwnershipEntry(0, 6, 7, 10)
        }
    }

    @Test
    fun `ownership entries reject invalid transfer fields`() {
        val scopeId = scope("main", 1).scopeId.raw

        assertFailsWith<IllegalArgumentException> { ScopeOwnershipEntry(scopeId, 0, 7, 10) }
        assertFailsWith<IllegalArgumentException> { ScopeOwnershipEntry(scopeId, 7, 0, 10) }
        assertFailsWith<IllegalArgumentException> { ScopeOwnershipEntry(scopeId, 7, 7, 10) }
        assertFailsWith<IllegalArgumentException> { ScopeOwnershipEntry(scopeId, 6, 7, -1) }
    }

    @Test
    fun `ownership history requires an ordered chain ending at its owner`() {
        val scope = scope("main", 1)
        val scopeId = scope.requireAssignedScopeId()
        val region = Region("region", 7, mutableListOf(scope))
        region.replaceOwnershipHistory(
            mapOf(
                scopeId to listOf(
                    ScopeOwnershipEntry(scopeId.raw, 5, 6, 10),
                    ScopeOwnershipEntry(scopeId.raw, 6, 7, 10)
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            region.replaceOwnershipHistory(
                mapOf(
                    scopeId to listOf(
                        ScopeOwnershipEntry(scopeId.raw, 5, 6, 11),
                        ScopeOwnershipEntry(scopeId.raw, 5, 7, 10)
                    )
                )
            )
        }
        assertEquals(listOf(6, 7), region.ownershipHistory(scopeId).map { it.toRegionNumberId })
    }

    @Test
    fun `legacy scope and ownership collections are detached snapshots`() {
        val scope = scope("main", 1)
        val region = Region("region", 7, mutableListOf(scope))
        region.recordOwnedScopeOwnership(ScopeOwnershipEntry(scope.scopeId.raw, 6, 7, 10))

        region.geometryScope.clear()
        region.ownershipHistoryByScope.getValue(scope.scopeId.raw).clear()

        assertEquals(listOf(scope), region.scopes)
        assertEquals(1, region.ownershipHistory(scope.requireAssignedScopeId()).size)
    }

    @Test
    fun `internal scope view cannot be cast into a mutation path`() {
        val scope = scope("main", 1)
        val region = Region("region", 7, mutableListOf(scope))

        assertFailsWith<UnsupportedOperationException> { (region.scopes as MutableList).clear() }

        assertEquals(listOf(scope), region.scopes)
    }

    @Test
    fun `region rejects duplicate scope names and assigned ids`() {
        val region = Region("region", 7, mutableListOf(scope("main", 1)))

        assertFailsWith<IllegalArgumentException> { region.addOwnedScope(scope("MAIN", 2)) }
        assertFailsWith<IllegalArgumentException> { region.addOwnedScope(scope("other", 1)) }
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

        val receipt = region.removeOwnedScope(first)
        region.restoreOwnedScope(receipt)

        assertEquals(listOf(first, second), region.scopes)
    }

    @Test
    fun `ordinary removal cannot empty a region`() {
        val only = scope("only", 1)
        val region = Region("region", 7, mutableListOf(only))

        assertFailsWith<IllegalArgumentException> { region.removeOwnedScope(only) }

        assertEquals(listOf(only), region.scopes)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy mutators remain callable but cannot change aggregate state`() {
        val first = scope("first", 1)
        val second = scope("second", 2)
        val region = Region("region", 7, mutableListOf(first, second))

        region.name = "region"
        region.showOnDynmap = true
        assertFailsWith<IllegalStateException> { region.addScope(scope("third", 3)) }
        assertFailsWith<IllegalStateException> { region.removeScope(first) }
        assertFailsWith<IllegalStateException> { region.restoreScope(0, scope("third", 3)) }
        assertFailsWith<IllegalStateException> { region.renameScope(first, "renamed") }
        assertFailsWith<IllegalStateException> {
            region.recordScopeOwnership(ScopeOwnershipEntry(first.scopeId.raw, 6, 7, 10))
        }

        assertEquals(listOf(first, second), region.scopes)
        assertEquals("first", first.scopeName)
    }

    private fun scope(name: String, id: Long) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(com.imyvm.iwg.domain.component.generateCompatScopeIdRaw(7, id.toInt()))
    )
}
