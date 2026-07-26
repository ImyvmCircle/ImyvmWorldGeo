package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

internal const val MAX_TELEPORT_FALLBACK_SEARCH_RADIUS = 8

internal fun requireTeleportFallbackSearchRadius(searchRadius: Int): Int {
    require(searchRadius in 0..MAX_TELEPORT_FALLBACK_SEARCH_RADIUS) {
        "search radius must be between 0 and $MAX_TELEPORT_FALLBACK_SEARCH_RADIUS"
    }
    return searchRadius
}

object TeleportConfig {
    @JvmField
    val TELEPORT_POINT_FALLBACK_SEARCH_RADIUS = Option(
        "core.teleport_point.fallback_search_radius",
        2,
        "the radius of the cubic search area used to find a safe fallback teleport point during creation or when the stored point is unsafe. " +
            "A radius of 2 means a 5x5x5 search cube; the supported range is " +
            "0..$MAX_TELEPORT_FALLBACK_SEARCH_RADIUS."
    ) { obj: Config, path: String? -> requireTeleportFallbackSearchRadius(obj.getInt(path)) }
}
