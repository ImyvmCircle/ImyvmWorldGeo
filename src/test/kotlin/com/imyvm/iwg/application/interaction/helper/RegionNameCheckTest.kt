package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.domain.Region
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionNameCheckTest {
    @Test
    fun `generated region and scope names satisfy the production validator`() {
        val regionName = CreationNameGenerator.generateRegionName()
        val scopeName = CreationNameGenerator.generateScopeName(Region("Owner", 7, mutableListOf()))

        assertTrue(isValidName(regionName))
        assertTrue(isValidName(scopeName))
        assertFalse(isValidName("invalid-name"))
    }
}
