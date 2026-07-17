package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RegionFactoryCompatibilityTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy scope factory requires exactly one creation source`() {
        assertFailsWith<IllegalArgumentException> {
            RegionFactory.createScope(
                scopeName = "scope",
                selectedPositions = rectanglePoints(),
                shapeType = GeoShapeType.RECTANGLE
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy reconstruction delegates to the typed world path`() {
        bindEmpty()
        val world = Identifier.parse("minecraft:overworld")
        val teleport = BlockPos(50, 64, 50)

        val result = RegionFactory.createScope(
            scopeName = "scope",
            existingWorld = world,
            existingTeleportPoint = teleport,
            selectedPositions = rectanglePoints(),
            shapeType = GeoShapeType.RECTANGLE
        )

        val scope = assertIs<Result.Ok<GeoScope>>(result).value
        assertEquals(world, scope.worldId)
        assertEquals(teleport, scope.teleportPoint)
    }

    private fun bindEmpty() {
        val directory = Files.createTempDirectory("iwg-region-factory-compatibility-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
    }

    private fun rectanglePoints() = mutableListOf(
        BlockPos.ZERO,
        BlockPos(100, 0, 100)
    )
}
