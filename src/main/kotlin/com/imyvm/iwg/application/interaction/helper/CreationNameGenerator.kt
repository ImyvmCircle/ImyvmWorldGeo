package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.domain.Region

object CreationNameGenerator {
    fun generateRegionName(): String = "NewRegion ${System.currentTimeMillis()}"

    fun generateScopeName(region: Region): String =
        "${region.name} NewScope ${System.currentTimeMillis()}"
}
