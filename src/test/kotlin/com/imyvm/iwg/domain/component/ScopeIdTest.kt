package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
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

        val allocated = RegionDatabase.nextScopeIdForNewScope(region)

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
            RegionDatabase.nextScopeIdForNewScope(region)
        }
    }

    private fun scope(regionId: Int, discriminator: Int, creationHours: Long) = GeoScope(
        "scope$discriminator",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateNewScopeIdRaw(regionId, 0, discriminator, creationHours))
    )
}
