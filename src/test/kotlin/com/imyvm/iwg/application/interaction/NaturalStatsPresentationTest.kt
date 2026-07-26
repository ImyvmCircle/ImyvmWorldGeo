package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.DimensionNaturalStats
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.RegionNaturalStats
import com.imyvm.iwg.util.text.Translator
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaturalStatsPresentationTest {
    private val structureA = Identifier.parse("minecraft:village")
    private val structureB = Identifier.parse("minecraft:stronghold")
    private val surface = Identifier.parse("minecraft:grass_block")
    private val biome = Identifier.parse("minecraft:plains")
    private val values = NaturalStatsLineValues(
        sampledColumnCount = 10,
        averageLocalDifficulty = 1.25,
        structureCounts = linkedMapOf(structureA to 2, structureB to 1),
        surfaceBlockCounts = linkedMapOf(surface to 5),
        biomeCounts = linkedMapOf(biome to 10)
    )

    @Test
    fun `region and dimension stats map to the same presentation values`() {
        val region = RegionNaturalStats(
            scopeCount = 2,
            candidateChunkCount = 4,
            loadedChunkCount = 3,
            sampledColumnCount = values.sampledColumnCount,
            averageLocalDifficulty = values.averageLocalDifficulty,
            structureCounts = values.structureCounts,
            surfaceBlockCounts = values.surfaceBlockCounts,
            biomeCounts = values.biomeCounts,
            dimensionStats = emptyMap()
        )
        val dimension = DimensionNaturalStats(
            scopeCount = 1,
            candidateChunkCount = 2,
            loadedChunkCount = 2,
            sampledColumnCount = values.sampledColumnCount,
            averageLocalDifficulty = values.averageLocalDifficulty,
            structureCounts = values.structureCounts,
            surfaceBlockCounts = values.surfaceBlockCounts,
            biomeCounts = values.biomeCounts
        )

        assertEquals(values, NaturalStatsLineValues.from(region))
        assertEquals(values, NaturalStatsLineValues.from(dimension))
    }

    @Test
    fun `all category preserves the established line order`() {
        val all = buildNaturalStatsCategoryLines(values, NaturalStatsCategory.ALL, 8).map { it.string }
        val individual = listOf(
            NaturalStatsCategory.DIFFICULTY,
            NaturalStatsCategory.STRUCTURES,
            NaturalStatsCategory.SURFACE,
            NaturalStatsCategory.BIOMES
        ).flatMap { buildNaturalStatsCategoryLines(values, it, 8) }.map { it.string }

        assertEquals(individual, all)
    }

    @Test
    fun `item limit truncates maps and reports remaining entries`() {
        val limited = buildNaturalStatsCategoryLines(values, NaturalStatsCategory.STRUCTURES, 1).single().string
        val complete = buildNaturalStatsCategoryLines(values, NaturalStatsCategory.STRUCTURES, 2).single().string

        assertTrue(limited.contains(structureA.toString()))
        assertFalse(limited.contains(structureB.toString()))
        assertTrue(complete.contains(structureB.toString()))
        assertTrue(limited.contains("1"))
    }

    @Test
    fun `empty unavailable and players categories retain existing output`() {
        val empty = NaturalStatsLineValues(0, null, emptyMap(), emptyMap(), emptyMap())

        assertEquals(
            Translator.tr(
                "interaction.meta.stats.line.difficulty",
                Translator.raw("interaction.meta.stats.not_available")
            ).string,
            buildNaturalStatsCategoryLines(empty, NaturalStatsCategory.DIFFICULTY, 8).single().string
        )
        assertEquals(
            Translator.tr(
                "interaction.meta.stats.line.structures",
                Translator.raw("interaction.meta.stats.none")
            ).string,
            buildNaturalStatsCategoryLines(empty, NaturalStatsCategory.STRUCTURES, 8).single().string
        )
        assertEquals(
            Translator.tr(
                "interaction.meta.stats.line.surface",
                Translator.raw("interaction.meta.stats.none")
            ).string,
            buildNaturalStatsCategoryLines(empty, NaturalStatsCategory.SURFACE, 8).single().string
        )
        assertTrue(buildNaturalStatsCategoryLines(empty, NaturalStatsCategory.PLAYERS, 8).isEmpty())
    }
}
