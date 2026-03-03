package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object TeleportConfig {
    @JvmField
    val TELEPORT_POINT_FALLBACK_SEARCH_RADIUS = Option("core.teleport_point.fallback_search_radius", 2,
        "the radius of the cubic search area used to find a safe fallback teleport point when the original is unsafe. A radius of 2 means a 5x5x5 search cube.") { obj: Config, path: String? -> obj.getInt(path) }
}
