package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.imyvm.iwg.domain.component.MAX_TELEPORT_FALLBACK_SEARCH_RADIUS
import com.imyvm.iwg.domain.component.requireTeleportFallbackSearchRadius
import com.typesafe.config.Config

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
