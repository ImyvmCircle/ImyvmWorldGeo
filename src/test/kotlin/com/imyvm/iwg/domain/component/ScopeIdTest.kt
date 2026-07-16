package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopeIdTest {
    @Test
    fun `parsing rejects unassigned and positive ids`() {
        assertNull(ScopeId.parse("s0"))
        assertNull(ScopeId.parse("s1"))
        assertNull(ScopeId.parse("sffffffffffffffff"))
        assertNull(AssignedScopeId.parse("s0"))
    }

    @Test
    fun `scope identity can only be assigned once`() {
        val scope = GeoScope("scope", Identifier.parse("minecraft:overworld"), null, geoShape = null)
        val first = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(7, 1)))
        val second = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(7, 2)))

        scope.assignScopeId(first)

        assertEquals(first, scope.requireAssignedScopeId())
        assertFailsWith<IllegalArgumentException> { scope.scopeId = second.toLegacyScopeId() }
    }

    @Test
    fun `scope rejects positive legacy identity`() {
        assertFailsWith<IllegalArgumentException> {
            GeoScope(
                "scope",
                Identifier.parse("minecraft:overworld"),
                null,
                geoShape = null,
                scopeId = ScopeId(42)
            )
        }
    }

    @Test
    fun `timed overlay rejects unassigned scope identity`() {
        assertFailsWith<IllegalArgumentException> {
            TimedEffectOverlay("overlay", ScopeId.UNASSIGNED_RAW, emptyList(), 0, 1, 0, "test")
        }
    }

    @Test
    fun `compatibility ids remain unique beyond the old 1024 scope limit`() {
        val first = ScopeId(generateCompatScopeIdRaw(7, 1024))
        val second = ScopeId(generateCompatScopeIdRaw(7, 2048))

        assertNotEquals(first, second)
        assertTrue(first.isCompatibility)
        assertNull(first.creationTimeMillisOrNull())
        assertEquals(0, first.mark())
        assertEquals(7, first.foundedInRegionNumberId())
    }

    @Test
    fun `new scope id allocation uses the remaining discriminator`() {
        val regionId = 7
        val creationHours = currentScopeCreationHours()
        val scopes = (0 until 63).mapTo(mutableListOf()) { discriminator ->
            scope(regionId, discriminator, creationHours)
        }
        val region = Region("region", regionId, scopes)

        val allocated = RegionDatabase.nextScopeIdForNewScope(
            region,
            listOf(region),
            firstDiscriminator = 0,
            creationHours = creationHours
        )

        assertEquals(63, ((allocated.raw ushr 32) and 0x3F).toInt())
    }

    @Test
    fun `new scope id allocation rejects only after every discriminator is occupied`() {
        val regionId = 7
        val creationHours = currentScopeCreationHours()
        val scopes = (0 until 64).mapTo(mutableListOf()) { discriminator ->
            scope(regionId, discriminator, creationHours)
        }
        val region = Region("region", regionId, scopes)

        assertFailsWith<ScopeIdCapacityExceededException> {
            RegionDatabase.nextScopeIdForNewScope(
                region,
                listOf(region),
                firstDiscriminator = 0,
                creationHours = creationHours
            )
        }
    }

    @Test
    fun `new scope id allocation includes scopes transferred to another region`() {
        val creationHours = currentScopeCreationHours()
        val transferred = scope(7, 17, creationHours)
        val source = Region("source", 7, mutableListOf(scope(7, 0, creationHours)))
        val target = Region("target", 8, mutableListOf(transferred))

        val allocated = RegionDatabase.nextScopeIdForNewScope(
            source,
            listOf(source, target),
            firstDiscriminator = 17,
            creationHours = creationHours
        )

        assertEquals(18, ((allocated.raw ushr 32) and 0x3F).toInt())
    }

    private fun scope(regionId: Int, discriminator: Int, creationHours: Long) = GeoScope(
        "scope$discriminator",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateNewScopeIdRaw(regionId, 0, discriminator, creationHours))
    )
}
