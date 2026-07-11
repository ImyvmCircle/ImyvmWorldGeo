package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeOwnershipEntry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.ScopeId
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
        val scope = GeoScope(
            "spawn",
            Identifier.parse("minecraft:overworld"),
            BlockPos(1, 64, 2),
            true,
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(10, 20, 30)),
            mutableListOf(PermissionSetting(PermissionKey.BUILD, false, player)),
            scopeId = ScopeId(42)
        )
        val region = Region("region", 7, mutableListOf(scope))
        region.settings += PermissionSetting(PermissionKey.PVP, false)
        region.ownershipHistoryByScope[42] = mutableListOf(ScopeOwnershipEntry(42, 6, 7, 1234))

        RegionDatabase.writeRegions(path, listOf(region))
        val loaded = RegionDatabase.readRegions(path).single()

        assertEquals("region", loaded.name)
        assertEquals(7, loaded.numberID)
        assertEquals(42L, loaded.geometryScope.single().scopeId.raw)
        assertEquals<List<Int>?>(listOf(10, 20, 30), loaded.geometryScope.single().geoShape?.shapeParameter)
        assertEquals(player, loaded.geometryScope.single().settings.single().playerUUID)
        assertEquals(1234L, loaded.ownershipHistoryByScope.getValue(42).single().changedAtMillis)
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
}
