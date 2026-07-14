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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegionDatabaseTest {
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
        val source = Region("source", 1, mutableListOf())
        val target = Region("target", 2, mutableListOf())
        val currentRegions = listOf(source, target)

        RegionDatabase.requireCanonicalRegions(source, target, currentRegions)

        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalRegions(
                Region("source", 1, mutableListOf()),
                target,
                currentRegions
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegionDatabase.requireCanonicalRegions(
                source,
                Region("target", 2, mutableListOf()),
                currentRegions
            )
        }
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
        region.recordScopeOwnership(ScopeOwnershipEntry(scopeId.raw, 6, 7, 1234))

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
        val region = Region("region", 7, mutableListOf(), settings)

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
            """{"version":1,"regions":{"7":{"entries":{"$player":"1"}}}}"""
        )

        invalidInputs.forEach { input ->
            Files.writeString(path, input)
            assertFailsWith<IOException> { RegionDatabase.readPlayerStats(path) }
            assertEquals(input, Files.readString(path))
        }
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
            directory.toFile().deleteRecursively()
        }
    }

    private fun writeMinimalRegion(stream: DataOutputStream, name: String, regionId: Int, scopeIdRaw: Long) {
        stream.writeUTF(name)
        stream.writeInt(regionId)
        stream.writeInt(1)
        stream.writeUTF("scope")
        stream.writeUTF("minecraft:overworld")
        stream.writeBoolean(false)
        stream.writeBoolean(false)
        stream.writeBoolean(false)
        stream.writeInt(0)
        stream.writeLong(scopeIdRaw)
        stream.writeInt(0)
        stream.writeInt(0)
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
