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

class RegionDatabaseTest {
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
}
