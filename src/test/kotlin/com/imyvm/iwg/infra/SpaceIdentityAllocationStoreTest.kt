package com.imyvm.iwg.infra

import com.imyvm.iwg.application.region.allocateRegionId
import com.imyvm.iwg.application.region.parseRegionCreationHours
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.generateNewScopeIdRaw
import com.imyvm.iwg.domain.component.parseScopeCreationHoursOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import net.minecraft.resources.Identifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SpaceIdentityAllocationStoreTest {
    @AfterTest
    fun tearDown() {
        SpaceIdentityAllocationStore.unbindSession()
        RegionDatabase.unbindSession()
        BehaviorStatsStore.unbindSession()
    }

    @Test
    fun `reserved identities survive restart and clock rollback`() = withTempDirectory { directory ->
        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 200, scopeHours = 300)
        val first = SpaceIdentityAllocationStore.reserveRegion(mark = 2, regionHours = 200, scopeHours = 300)
        assertEquals(1L, SpaceIdentityAllocationStore.reserveSubSpaceId())
        SpaceIdentityAllocationStore.unbindSession()

        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 1, scopeHours = 1)
        val second = SpaceIdentityAllocationStore.reserveRegion(mark = 2, regionHours = 1, scopeHours = 1)

        assertNotEquals(first.regionId, second.regionId)
        assertNotEquals(first.mainScopeId, second.mainScopeId)
        assertEquals(200, parseRegionCreationHours(second.regionId))
        assertEquals(300L, parseScopeCreationHoursOrNull(second.mainScopeId.raw))
        assertEquals(2L, SpaceIdentityAllocationStore.reserveSubSpaceId())
    }

    @Test
    fun `deleting a published region does not release its identities`() = withTempDirectory { directory ->
        RegionDatabase.bindSession(directory)
        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 200, scopeHours = 300)
        val deleted = SpaceIdentityAllocationStore.reserveRegion(mark = 2, regionHours = 200, scopeHours = 300)
        val region = Region(
            "deleted",
            deleted.regionId,
            mutableListOf(
                GeoScope(
                    "main_scope",
                    Identifier.parse("minecraft:overworld"),
                    null,
                    geoShape = null,
                    scopeId = deleted.mainScopeId.toLegacyScopeId()
                )
            )
        )
        RegionDatabase.addRegion(region)
        RegionDatabase.removeRegion(region)

        val replacement = SpaceIdentityAllocationStore.reserveRegion(mark = 2, regionHours = 200, scopeHours = 300)

        assertNotEquals(deleted.regionId, replacement.regionId)
        assertNotEquals(deleted.mainScopeId, replacement.mainScopeId)
    }

    @Test
    fun `migration advances beyond identities found in old behavior stats`() = withTempDirectory { directory ->
        val regionId = allocateRegionId(mark = 3, hoursFromEpoch = 100, existingIds = emptySet(), initialDiscriminator = 9)
        val scopeId = generateNewScopeIdRaw(regionId, mark = 3, discriminator = 12, creationHours = 100)
        BehaviorStatsStore.writeStats(
            directory.resolve("iwg_behavior_stats.json"),
            mapOf(
                BehaviorStatsKey(
                    NaturalPeriodKind.DAY,
                    "2026-07-30",
                    WorldGeoBehaviorType.DEBUG_TEST,
                    regionId,
                    scopeId,
                    78L,
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "migration"
                ) to 1L
            )
        )

        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 50, scopeHours = 50)
        val reservedRegion = SpaceIdentityAllocationStore.reserveRegion(mark = 3, regionHours = 50, scopeHours = 50)
        val reservedScope = SpaceIdentityAllocationStore.reserveScope(regionId, mark = 3, scopeHours = 50)

        assertEquals(10, reservedRegion.regionId and 0x7F)
        assertEquals(14, ((reservedScope.raw ushr 32) and 0x3F).toInt())
        assertEquals(79L, SpaceIdentityAllocationStore.reserveSubSpaceId())
    }

    @Test
    fun `failed durable write does not advance in-memory allocation`() = withTempDirectory { directory ->
        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 100, scopeHours = 100)
        val statePath = directory.resolve("iwg_space_identity_allocations.json")
        Files.delete(statePath)
        Files.createDirectory(statePath)

        assertFailsWith<IOException> {
            SpaceIdentityAllocationStore.reserveRegion(mark = 1, regionHours = 100, scopeHours = 100)
        }

        Files.delete(statePath)
        val reserved = SpaceIdentityAllocationStore.reserveRegion(mark = 1, regionHours = 100, scopeHours = 100)
        assertEquals(0, reserved.regionId and 0x7F)
        assertEquals(0, ((reserved.mainScopeId.raw ushr 32) and 0x3F).toInt())
    }

    @Test
    fun `malformed allocation state is rejected without replacement`() = withTempDirectory { directory ->
        val statePath = directory.resolve("iwg_space_identity_allocations.json")
        val malformed = """{"formatVersion":1,"regionHours":100}"""
        Files.writeString(statePath, malformed)

        assertFailsWith<IOException> {
            SpaceIdentityAllocationStore.bindSession(directory, regionHours = 100, scopeHours = 100)
        }

        assertEquals(malformed, Files.readString(statePath))
        SpaceIdentityAllocationStore.bindSession(directory.resolve("valid"), regionHours = 100, scopeHours = 100)
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-space-identity-test")
        try {
            block(directory)
        } finally {
            SpaceIdentityAllocationStore.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }
}
