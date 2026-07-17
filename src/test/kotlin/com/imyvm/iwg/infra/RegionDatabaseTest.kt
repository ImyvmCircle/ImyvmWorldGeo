package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeOwnershipEntry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.*
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegionDatabaseTest {
    @Test
    fun `binds data to one world root at a time`() = withTempDirectory { directory ->
        val firstRoot = directory.resolve("first")
        val secondRoot = directory.resolve("second")
        val region = sessionRegion().apply {
            setDynmapVisibility(false)
            scopes.single().setDynmapVisibility(false)
        }
        val player = UUID.randomUUID()

        RegionDatabase.bindSession(firstRoot)
        RegionDatabase.insertRegion(region)
        RegionDatabase.recordRegionEntry(region, player)
        RegionDatabase.saveForShutdown()
        RegionDatabase.unbindSession()

        RegionDatabase.bindSession(secondRoot)
        assertTrue(RegionDatabase.getRegionList().isEmpty())
        RegionDatabase.saveForShutdown()
        RegionDatabase.unbindSession()

        RegionDatabase.bindSession(firstRoot)
        val loaded = RegionDatabase.getRegionList().single()
        assertEquals("region", loaded.name)
        assertFalse(loaded.showOnDynmap)
        assertFalse(loaded.scopes.single().showOnDynmap)
        assertEquals(1L, RegionDatabase.getRegionPlayerStats(loaded).entryCount)
    }

    @Test
    fun `failed bind restores the unbound state`() = withTempDirectory { directory ->
        val invalidRoot = directory.resolve("invalid")
        val validRoot = directory.resolve("valid")
        Files.createDirectories(invalidRoot)
        val orphan = invalidRoot.resolve("iwg_dynmap.json")
        Files.writeString(orphan, "original")

        assertFailsWith<IOException> { RegionDatabase.bindSession(invalidRoot) }

        assertFalse(RegionDatabase.hasActiveSession())
        assertTrue(RegionDatabase.getRegionList().isEmpty())
        assertEquals("original", Files.readString(orphan))
        RegionDatabase.bindSession(validRoot)
        assertTrue(RegionDatabase.hasActiveSession())
    }

    @Test
    fun `session lifecycle misuse fails without replacing active data`() = withTempDirectory { directory ->
        val firstRoot = directory.resolve("first")
        val secondRoot = directory.resolve("second")
        RegionDatabase.bindSession(firstRoot)
        RegionDatabase.insertRegion(sessionRegion())

        assertFailsWith<IllegalStateException> { RegionDatabase.bindSession(secondRoot) }
        assertEquals("region", RegionDatabase.getRegionList().single().name)

        RegionDatabase.unbindSession()
        assertFailsWith<IllegalStateException> { RegionDatabase.save() }
        assertFailsWith<IllegalStateException> { RegionDatabase.saveForShutdown() }
        RegionDatabase.savePlayerStatsSnapshot()
        @Suppress("DEPRECATION")
        assertFailsWith<IllegalStateException> { RegionDatabase.load() }
    }

    @Test
    fun `player stats snapshot reports whether persistence succeeded`() = withTempDirectory { directory ->
        var saveCalls = 0
        assertFalse(RegionDatabase.trySavePlayerStatsSnapshot { saveCalls++ })
        assertEquals(0, saveCalls)

        RegionDatabase.bindSession(directory)
        val region = sessionRegion()
        RegionDatabase.insertRegion(region)
        RegionDatabase.recordRegionEntry(region, UUID.randomUUID())
        assertTrue(RegionDatabase.trySavePlayerStatsSnapshot())
        assertTrue(Files.exists(directory.resolve("iwg_player_stats.json")))

        assertTrue(RegionDatabase.trySavePlayerStatsSnapshot { saveCalls++ })
        assertEquals(1, saveCalls)

        assertFalse(RegionDatabase.trySavePlayerStatsSnapshot {
            saveCalls++
            throw IOException("simulated snapshot failure")
        })
        assertEquals(2, saveCalls)
    }

    @Test
    fun `shutdown save persists without invoking projections`() = withTempDirectory { directory ->
        RegionDatabase.bindSession(directory)
        RegionDatabase.insertRegion(sessionRegion())
        var projectionCalls = 0
        RegionDatabase.onSave = { projectionCalls++ }

        RegionDatabase.save()
        assertEquals(1, projectionCalls)

        RegionDatabase.saveForShutdown()
        assertEquals(1, projectionCalls)

        RegionDatabase.onSave = {
            projectionCalls++
            throw IOException("projection failed")
        }
        RegionDatabase.save()
        assertEquals(2, projectionCalls)
        assertTrue(Files.exists(directory.resolve("iwg_regions.db")))
    }

    @Test
    fun `rejects orphan companion files without modifying them`() = withTempDirectory { directory ->
        val dynmap = directory.resolve("dynmap.json")
        val playerStats = directory.resolve("player-stats.json")
        val companionFiles = listOf(dynmap, playerStats)

        RegionDatabase.rejectOrphanCompanionFiles(companionFiles)

        companionFiles.forEach { orphan ->
            Files.writeString(orphan, "original")

            assertFailsWith<IOException> {
                RegionDatabase.rejectOrphanCompanionFiles(companionFiles)
            }
            assertEquals("original", Files.readString(orphan))
            Files.delete(orphan)
        }
    }

    @Test
    fun `region mutations require both canonical database objects`() {
        val source = regionWithScope("source", 1)
        val target = regionWithScope("target", 2)
        val currentRegions = listOf(source, target)

        RegionDatabase.requireCanonicalRegions(source, target, currentRegions)

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalRegions(
                regionWithScope("source", 1),
                target,
                currentRegions
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalRegions(
                source,
                regionWithScope("target", 2),
                currentRegions
            )
        }
    }

    @Test
    fun `region and scope mutations require exact canonical targets`() {
        val scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        val canonicalScope = scope("scope", scopeId)
        val canonicalRegion = Region("region", 7, mutableListOf(canonicalScope))
        val currentRegions = listOf(canonicalRegion)

        RegionDatabase.requireCanonicalRegion(canonicalRegion, currentRegions)
        RegionDatabase.requireCanonicalScope(canonicalRegion, canonicalScope, currentRegions)

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalRegion(
                Region("region", 7, mutableListOf(scope("scope", scopeId))),
                currentRegions
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalScope(
                canonicalRegion,
                scope("scope", scopeId),
                currentRegions
            )
        }
        assertFailsWith<IllegalArgumentException> {
            val detachedScope = scope("scope", scopeId)
            RegionDatabase.requireCanonicalScope(
                Region("region", 7, mutableListOf(detachedScope)),
                detachedScope,
                currentRegions
            )
        }
    }

    @Test
    fun `region list is a detached registry snapshot`() = withTempDirectory { directory ->
        RegionDatabase.bindSession(directory)
        val first = regionWithScope("first", 7)
        val second = regionWithScope("second", 8)
        RegionDatabase.insertRegion(first)
        RegionDatabase.insertRegion(second)

        val snapshot = RegionDatabase.getRegionList()
        (snapshot as MutableList).clear()

        assertEquals(listOf(first, second), RegionDatabase.getRegionList())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy database mutations remain callable but cannot change live state`() = withTempDirectory { directory ->
        RegionDatabase.bindSession(directory)
        val region = regionWithScope("region", 7)
        RegionDatabase.insertRegion(region)

        assertFailsWith<IllegalStateException> { RegionDatabase.addRegion(regionWithScope("other", 8)) }
        assertFailsWith<IllegalStateException> { RegionDatabase.removeRegion(region) }
        assertFailsWith<IllegalStateException> { RegionDatabase.renameRegion(region, "renamed") }
        assertFailsWith<IllegalStateException> {
            RegionDatabase.incrementRegionEntryStat(region, UUID.randomUUID())
        }

        assertEquals(listOf(region), RegionDatabase.getRegionList())
        assertEquals("region", region.name)
        assertEquals(0L, RegionDatabase.getRegionPlayerStats(region).entryCount)
    }

    @Test
    fun `player stat mutation rejects detached region`() = withTempDirectory { directory ->
        RegionDatabase.bindSession(directory)
        val canonical = regionWithScope("region", 7)
        RegionDatabase.insertRegion(canonical)

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.recordRegionEntry(regionWithScope("region", 7), UUID.randomUUID())
        }

        assertEquals(0L, RegionDatabase.getRegionPlayerStats(canonical).entryCount)
    }

    @Test
    fun `round trips current database format`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        val player = UUID.randomUUID()
        val scopeId = ScopeId(generateCompatScopeIdRaw(7, 42))
        val scope = GeoScope(
            "spawn",
            Identifier.parse("minecraft:overworld"),
            BlockPos(1, 64, 2),
            true,
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(10, 20, 30)),
            mutableListOf(PermissionSetting(PermissionKey.BUILD, false, player)),
            scopeId = scopeId
        )
        val region = Region("region", 7, mutableListOf(scope))
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))
        region.recordOwnedScopeOwnership(ScopeOwnershipEntry(scopeId.raw, 6, 7, 1234))

        RegionDatabase.writeRegions(path, listOf(region))
        val loaded = RegionDatabase.readRegions(path).single()

        assertEquals("region", loaded.name)
        assertEquals(7, loaded.numberID)
        assertEquals(scopeId.raw, loaded.geometryScope.single().scopeId.raw)
        assertEquals<List<Int>?>(listOf(10, 20, 30), loaded.geometryScope.single().geoShape?.shapeParameter)
        assertEquals(player, loaded.geometryScope.single().settings.single().playerUUID)
        assertEquals(false, (loaded.settings.single() as PermissionSetting).value)
        assertEquals(1234L, loaded.ownershipHistoryByScope.getValue(scopeId.raw).single().changedAtMillis)
    }

    @Test
    fun `round trips every legacy setting tag through the typed store`() = withTempDirectory { directory ->
        val path = directory.resolve("settings.db")
        val settings = mutableListOf<Setting>(
            PermissionSetting(PermissionKey.PVP, false),
            EffectSetting(EffectKey.SPEED, 2),
            RuleSetting(RuleKey.DISPENSER, false),
            EntryExitToggleSetting(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED, false),
            EntryExitMessageSetting(EntryExitMessageKey.ENTER_MESSAGE, "hello"),
            ExtensionPermissionSetting(ExtensionPermissionKey("test:permission"), true),
            ExtensionRuleSetting(ExtensionRuleKey("test:rule"), true)
        )
        val region = Region(
            "region",
            7,
            mutableListOf(scope("scope", ScopeId(generateCompatScopeIdRaw(7, 0)))),
            settings
        )

        RegionDatabase.writeRegions(path, listOf(region))
        val loaded = RegionDatabase.readRegions(path).single().settings

        assertEquals(7, loaded.size)
        assertEquals(
            settings.associate { it.key to (it.javaClass to it.value) },
            loaded.associate { it.key to (it.javaClass to it.value) }
        )
    }

    @Test
    fun `rejects collection counts before allocation`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(-1)
            it.writeInt(Int.MAX_VALUE)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects invalid shape ordinal`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(-1)
            it.writeInt(1)
            it.writeUTF("region")
            it.writeInt(7)
            it.writeInt(1)
            it.writeUTF("scope")
            it.writeUTF("minecraft:overworld")
            it.writeBoolean(false)
            it.writeBoolean(false)
            it.writeBoolean(true)
            it.writeInt(Int.MAX_VALUE)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects invalid shape parameter count`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(-1)
            it.writeInt(1)
            it.writeUTF("region")
            it.writeInt(7)
            it.writeInt(1)
            it.writeUTF("scope")
            it.writeUTF("minecraft:overworld")
            it.writeBoolean(false)
            it.writeBoolean(false)
            it.writeBoolean(true)
            it.writeInt(GeoShapeType.CIRCLE.ordinal)
            it.writeInt(2)
            it.writeInt(10)
            it.writeInt(20)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects structurally invalid polygon geometry`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(-1)
            it.writeInt(1)
            it.writeUTF("region")
            it.writeInt(7)
            it.writeInt(1)
            it.writeUTF("scope")
            it.writeUTF("minecraft:overworld")
            it.writeBoolean(false)
            it.writeBoolean(false)
            it.writeBoolean(true)
            it.writeInt(GeoShapeType.POLYGON.ordinal)
            it.writeInt(6)
            listOf(0, 0, 10, 0, 20, 0).forEach(it::writeInt)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects oversized polygon parameter count before reading coordinates`() = withTempDirectory { directory ->
        val path = directory.resolve("regions.db")
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(-1)
            it.writeInt(1)
            it.writeUTF("region")
            it.writeInt(7)
            it.writeInt(1)
            it.writeUTF("scope")
            it.writeUTF("minecraft:overworld")
            it.writeBoolean(false)
            it.writeBoolean(false)
            it.writeBoolean(true)
            it.writeInt(GeoShapeType.POLYGON.ordinal)
            it.writeInt(514)
        }

        val error = assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
        assertTrue(error.message.orEmpty().contains("Invalid parameter count 514"))
    }

    @Test
    fun `round trips polygon at the 256 vertex limit`() = withTempDirectory { directory ->
        val path = directory.resolve("polygon-limit.db")
        val scopeId = ScopeId(generateCompatScopeIdRaw(7, 0))
        val parameters = squarePolygonParameters()
        val scope = GeoScope(
            "polygon",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape(GeoShapeType.POLYGON, parameters),
            scopeId = scopeId
        )

        RegionDatabase.writeRegions(path, listOf(Region("region", 7, mutableListOf(scope))))
        val loaded = RegionDatabase.readRegions(path).single().geometryScope.single().geoShape

        assertEquals(parameters, loaded?.shapeParameter)
    }

    @Test
    fun `rejects unassigned scope id in current database format`() = withTempDirectory { directory ->
        val path = directory.resolve("invalid-scope-id.db")
        DataOutputStream(Files.newOutputStream(path)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            writeMinimalRegion(stream, "region", 7, ScopeId.UNASSIGNED_RAW)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects duplicate scope ids across regions`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-scope-id.db")
        val duplicate = generateCompatScopeIdRaw(7, 0)
        DataOutputStream(Files.newOutputStream(path)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(2)
            writeMinimalRegion(stream, "first", 7, duplicate)
            writeMinimalRegion(stream, "second", 8, duplicate)
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects writing duplicate scope ids across regions`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-scope-id.db")
        Files.writeString(path, "original")
        val duplicate = ScopeId(generateCompatScopeIdRaw(7, 0))
        val first = Region("first", 7, mutableListOf(scope("first", duplicate)))
        val second = Region("second", 8, mutableListOf(scope("second", duplicate)))

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.writeRegions(path, listOf(first, second))
        }
        assertEquals("original", Files.readString(path))
    }

    @Test
    fun `rejects duplicate region ids`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-region-id.db")
        DataOutputStream(Files.newOutputStream(path)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(2)
            writeMinimalRegion(stream, "first", 7, generateCompatScopeIdRaw(7, 0))
            writeMinimalRegion(stream, "second", 7, generateCompatScopeIdRaw(7, 1))
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects case insensitive duplicate region names on read`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-region-name.db")
        DataOutputStream(Files.newOutputStream(path)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(2)
            writeMinimalRegion(stream, "Region", 7, generateCompatScopeIdRaw(7, 0))
            writeMinimalRegion(stream, "region", 8, generateCompatScopeIdRaw(8, 0))
        }

        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
    }

    @Test
    fun `rejects empty regions and invalid persisted names without rewriting the file`() = withTempDirectory { directory ->
        val emptyRegion = directory.resolve("empty-region.db")
        DataOutputStream(Files.newOutputStream(emptyRegion)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            stream.writeUTF("region")
            stream.writeInt(7)
            stream.writeInt(0)
            stream.writeInt(0)
            stream.writeInt(0)
        }
        assertMalformedRegionFileUnchanged(emptyRegion)

        listOf("1region" to "scope", "region" to "scope_").forEachIndexed { index, (regionName, scopeName) ->
            val path = directory.resolve("invalid-name-$index.db")
            DataOutputStream(Files.newOutputStream(path)).use { stream ->
                stream.writeInt(-1)
                stream.writeInt(1)
                writeMinimalRegion(
                    stream,
                    regionName,
                    7,
                    generateCompatScopeIdRaw(7, index),
                    scopeName = scopeName
                )
            }
            assertMalformedRegionFileUnchanged(path)
        }
    }

    @Test
    fun `rejects invalid and duplicate persisted settings without rewriting the file`() = withTempDirectory { directory ->
        val invalidEffect = directory.resolve("invalid-effect.db")
        DataOutputStream(Files.newOutputStream(invalidEffect)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            writeMinimalRegion(
                stream,
                "region",
                7,
                generateCompatScopeIdRaw(7, 0),
                writeRegionSettings = {
                    it.writeInt(1)
                    it.writeInt(1)
                    it.writeInt(EffectKey.SPEED.ordinal)
                    it.writeInt(256)
                    it.writeUTF("")
                }
            )
        }
        assertMalformedRegionFileUnchanged(invalidEffect)

        val duplicate = directory.resolve("duplicate-setting.db")
        DataOutputStream(Files.newOutputStream(duplicate)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            writeMinimalRegion(
                stream,
                "region",
                7,
                generateCompatScopeIdRaw(7, 0),
                writeRegionSettings = {
                    it.writeInt(2)
                    repeat(2) { index ->
                        it.writeInt(0)
                        it.writeInt(PermissionKey.BUILD.ordinal)
                        it.writeBoolean(index == 0)
                        it.writeUTF("")
                    }
                }
            )
        }
        assertMalformedRegionFileUnchanged(duplicate)
    }

    @Test
    fun `rejects broken and duplicate persisted ownership histories without rewriting the file`() = withTempDirectory { directory ->
        val scopeId = generateCompatScopeIdRaw(7, 0)
        val broken = directory.resolve("broken-history.db")
        DataOutputStream(Files.newOutputStream(broken)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            writeMinimalRegion(
                stream,
                "region",
                7,
                scopeId,
                ownershipHistory = listOf(
                    scopeId to listOf(
                        ScopeOwnershipEntry(scopeId, 5, 6, 20),
                        ScopeOwnershipEntry(scopeId, 5, 7, 10)
                    )
                )
            )
        }
        assertMalformedRegionFileUnchanged(broken)

        val duplicate = directory.resolve("duplicate-history.db")
        val entry = ScopeOwnershipEntry(scopeId, 6, 7, 10)
        DataOutputStream(Files.newOutputStream(duplicate)).use { stream ->
            stream.writeInt(-1)
            stream.writeInt(1)
            writeMinimalRegion(
                stream,
                "region",
                7,
                scopeId,
                ownershipHistory = listOf(scopeId to listOf(entry), scopeId to listOf(entry))
            )
        }
        assertMalformedRegionFileUnchanged(duplicate)
    }

    @Test
    fun `rejects ownership history stored by multiple regions`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-history-owner.db")
        val historyScopeId = generateCompatScopeIdRaw(9, 0)
        val first = regionWithScope("first", 7).apply {
            recordOwnedScopeOwnership(ScopeOwnershipEntry(historyScopeId, 6, 7, 10))
        }
        val second = regionWithScope("second", 8).apply {
            recordOwnedScopeOwnership(ScopeOwnershipEntry(historyScopeId, 6, 8, 10))
        }

        assertFailsWith<IllegalArgumentException> { RegionDatabase.writeRegions(path, listOf(first, second)) }
        assertFalse(Files.exists(path))
    }

    @Test
    fun `rejects case insensitive duplicate region names before write`() = withTempDirectory { directory ->
        val path = directory.resolve("duplicate-region-name.db")
        Files.writeString(path, "original")
        val first = Region("Region", 7, mutableListOf(scope("first", ScopeId(generateCompatScopeIdRaw(7, 0)))))
        val second = Region("region", 8, mutableListOf(scope("second", ScopeId(generateCompatScopeIdRaw(8, 0)))))

        assertFailsWith<IllegalArgumentException> { RegionDatabase.writeRegions(path, listOf(first, second)) }
        assertEquals("original", Files.readString(path))
    }

    @Test
    fun `failed atomic write preserves existing file`() = withTempDirectory { directory ->
        val path = directory.resolve("data.json")
        Files.writeString(path, "original")

        assertFailsWith<IOException> {
            RegionDatabase.atomicWrite(path) {
                it.write("partial".toByteArray())
                throw IOException("simulated failure")
            }
        }

        assertEquals("original", Files.readString(path))
        assertEquals(listOf("data.json"), Files.list(directory).use { files -> files.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `reads valid player stats before adopting them`() = withTempDirectory { directory ->
        val path = directory.resolve("player-stats.json")
        val player = UUID.randomUUID()
        Files.writeString(path, """
            {
              "version": 1,
              "regions": {
                "7": {
                  "entries": {"$player": 2},
                  "stayMillis": {"$player": 3000}
                }
              }
            }
        """.trimIndent())

        val stats = RegionDatabase.readPlayerStats(path).getValue(7).aggregate()

        assertEquals(1, stats.trackedPlayerCount)
        assertEquals(2, stats.entryCount)
        assertEquals(3000, stats.stayMillis)
    }

    @Test
    fun `player stats aggregate and merge fail without partial mutation on overflow`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val overflowingAggregate = RegionPlayerStatsLedger(
            entryCounts = mutableMapOf(first to Long.MAX_VALUE, second to 1L)
        )
        assertFailsWith<ArithmeticException> { overflowingAggregate.aggregate() }

        val target = RegionPlayerStatsLedger(
            entryCounts = mutableMapOf(first to 2L),
            stayMillis = mutableMapOf(first to Long.MAX_VALUE)
        )
        val original = target.detachedCopy()
        val source = RegionPlayerStatsLedger(
            entryCounts = mutableMapOf(first to 3L),
            stayMillis = mutableMapOf(first to 1L)
        )

        assertFailsWith<ArithmeticException> { target.mergeFrom(source) }
        assertEquals(original, target)
    }

    @Test
    fun `overflowing database stats merge keeps both regions and ledgers`() = withTempDirectory { directory ->
        val player = UUID.randomUUID()
        val source = regionWithScope("source", 1)
        val target = regionWithScope("target", 2)
        RegionDatabase.writeRegions(directory.resolve("iwg_regions.db"), listOf(source, target))
        Files.writeString(
            directory.resolve("iwg_player_stats.json"),
            """{"version":1,"regions":{"1":{"entries":{"$player":${Long.MAX_VALUE}}},"2":{"entries":{"$player":1}}}}"""
        )
        RegionDatabase.bindSession(directory)
        val loadedSource = RegionDatabase.getRegionByNumberId(1)
        val loadedTarget = RegionDatabase.getRegionByNumberId(2)

        assertFailsWith<ArithmeticException> {
            RegionDatabase.mergeAndRemoveRegionReversibly(loadedSource, loadedTarget)
        }

        assertEquals(2, RegionDatabase.getRegionList().size)
        assertEquals(Long.MAX_VALUE, RegionDatabase.getRegionPlayerStats(loadedSource).entryCount)
        assertEquals(1L, RegionDatabase.getRegionPlayerStats(loadedTarget).entryCount)
    }

    @Test
    fun `round trips player stats JSON`() = withTempDirectory { directory ->
        val path = directory.resolve("player-stats.json")
        val player = UUID.randomUUID()
        val ledger = RegionPlayerStatsLedger(entryCounts = mutableMapOf(player to 2L))

        RegionDatabase.writePlayerStats(path, listOf(7), mapOf(7 to ledger))
        val loaded = RegionDatabase.readPlayerStats(path).getValue(7)

        assertEquals(ledger, loaded)
    }

    @Test
    fun `rejects malformed player stats without rewriting the file`() = withTempDirectory { directory ->
        val path = directory.resolve("player-stats.json")
        val player = UUID.randomUUID()
        val invalidInputs = listOf(
            "[1]",
            """{"version":1,"regions":[]}""",
            """{"version":1,"regions":{"7":{"entries":{"not-a-uuid":1}}}}""",
            """{"version":1,"regions":{"7":{"entries":{"$player":"1"}}}}""",
            """{"version":1,"regions":{"7":{"entries":{"$player":${Long.MAX_VALUE},"00000000-0000-0000-0000-000000000001":1}}}}"""
        )

        invalidInputs.forEach { input ->
            Files.writeString(path, input)
            assertFailsWith<IOException> { RegionDatabase.readPlayerStats(path) }
            assertEquals(input, Files.readString(path))
        }
    }

    @Test
    fun `player stats writer rejects non-positive values without replacing the file`() = withTempDirectory { directory ->
        val path = directory.resolve("player-stats.json")
        val player = UUID.randomUUID()
        Files.writeString(path, "original")

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.writePlayerStats(
                path,
                listOf(7),
                mapOf(7 to RegionPlayerStatsLedger(entryCounts = mutableMapOf(player to -1L)))
            )
        }

        assertEquals("original", Files.readString(path))
    }

    @Test
    fun `reads valid Dynmap visibility with defaults`() = withTempDirectory { directory ->
        val path = directory.resolve("dynmap.json")
        Files.writeString(path, """
            {
              "regions": {
                "7": {
                  "showOnDynmap": false,
                  "scopes": {"spawn": true}
                },
                "8": {}
              }
            }
        """.trimIndent())

        val visibility = RegionDatabase.readDynmapVisibility(path)

        assertEquals(false, visibility.getValue(7).showOnDynmap)
        assertEquals(true, visibility.getValue(7).scopes.getValue("spawn"))
        assertEquals(true, visibility.getValue(8).showOnDynmap)
    }

    @Test
    fun `round trips Dynmap visibility JSON`() = withTempDirectory { directory ->
        val path = directory.resolve("dynmap.json")
        val scope = scope("spawn", ScopeId(generateCompatScopeIdRaw(7, 0))).apply { setDynmapVisibility(false) }
        val region = Region("region", 7, mutableListOf(scope), showOnDynmap = false)

        RegionDatabase.writeDynmapVisibility(path, listOf(region))
        val loaded = RegionDatabase.readDynmapVisibility(path).getValue(7)

        assertEquals(false, loaded.showOnDynmap)
        assertEquals(false, loaded.scopes.getValue("spawn"))
    }

    @Test
    fun `rejects malformed Dynmap visibility without rewriting the file`() = withTempDirectory { directory ->
        val path = directory.resolve("dynmap.json")
        val invalidInputs = listOf(
            "{",
            """{"regions":[]}""",
            """{"regions":{"7":{"showOnDynmap":"true"}}}""",
            """{"regions":{"7":{"scopes":{"spawn":1}}}}"""
        )

        invalidInputs.forEach { input ->
            Files.writeString(path, input)
            assertFailsWith<IOException> { RegionDatabase.readDynmapVisibility(path) }
            assertEquals(input, Files.readString(path))
        }
    }

    private fun withTempDirectory(block: (java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-region-database-test")
        try {
            block(directory)
        } finally {
            RegionDatabase.onSave = null
            RegionDatabase.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }

    private fun sessionRegion(): Region {
        val regionId = 7
        val assignedScopeId = ScopeId(generateCompatScopeIdRaw(regionId, 0))
        return Region("region", regionId, mutableListOf(scope("scope", assignedScopeId)))
    }

    private fun regionWithScope(name: String, regionId: Int): Region = Region(
        name,
        regionId,
        mutableListOf(scope("scope", ScopeId(generateCompatScopeIdRaw(regionId, 0))))
    )

    private fun writeMinimalRegion(
        stream: DataOutputStream,
        name: String,
        regionId: Int,
        scopeIdRaw: Long,
        scopeName: String = "scope",
        writeRegionSettings: (DataOutputStream) -> Unit = { it.writeInt(0) },
        ownershipHistory: List<Pair<Long, List<ScopeOwnershipEntry>>> = emptyList()
    ) {
        stream.writeUTF(name)
        stream.writeInt(regionId)
        stream.writeInt(1)
        stream.writeUTF(scopeName)
        stream.writeUTF("minecraft:overworld")
        stream.writeBoolean(false)
        stream.writeBoolean(false)
        stream.writeBoolean(false)
        stream.writeInt(0)
        stream.writeLong(scopeIdRaw)
        writeRegionSettings(stream)
        stream.writeInt(ownershipHistory.size)
        ownershipHistory.forEach { (key, entries) ->
            stream.writeLong(key)
            stream.writeInt(entries.size)
            entries.forEach { entry ->
                stream.writeLong(entry.scopeIdRaw)
                stream.writeInt(entry.fromRegionNumberId)
                stream.writeInt(entry.toRegionNumberId)
                stream.writeLong(entry.changedAtMillis)
            }
        }
    }

    private fun assertMalformedRegionFileUnchanged(path: java.nio.file.Path) {
        val original = Files.readAllBytes(path)
        assertFailsWith<IOException> { RegionDatabase.readRegions(path) }
        assertTrue(original.contentEquals(Files.readAllBytes(path)))
    }

    private fun scope(name: String, scopeId: ScopeId) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = scopeId
    )

    private fun squarePolygonParameters(): MutableList<Int> = buildList {
        for (x in 0 until 128 step 2) addAll(listOf(x, 0))
        for (z in 0 until 128 step 2) addAll(listOf(128, z))
        for (x in 128 downTo 2 step 2) addAll(listOf(x, 128))
        for (z in 128 downTo 2 step 2) addAll(listOf(0, z))
    }.toMutableList()
}
