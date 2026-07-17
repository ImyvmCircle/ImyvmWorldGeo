package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionNameCheckTest {
    @Test
    fun `generated region and scope names satisfy the production validator`() {
        val regionName = CreationNameGenerator.generateRegionName()
        val scopeName = CreationNameGenerator.generateScopeName(region("Owner"))

        assertTrue(isValidName(regionName))
        assertTrue(isValidName(scopeName))
        assertTrue(scopeName.startsWith("Owner NewScope "))
    }

    @Test
    fun `names use letters first and isolated supported separators`() {
        listOf("Region 1", "Region_1", "Region-1", "Player's Region", "main_scope", "领地 1")
            .forEach { assertTrue(isValidName(it), it) }
        listOf("1Region", "_Region", "Region_", "Region--1", "Region - 1", "123", " Region")
            .forEach { assertFalse(isValidName(it), it) }
    }

    private fun region(name: String): Region = Region(
        name,
        7,
        mutableListOf(
            GeoScope(
                "main",
                Identifier.parse("minecraft:overworld"),
                null,
                geoShape = null,
                scopeId = ScopeId(generateCompatScopeIdRaw(7, 0))
            )
        )
    )
}
