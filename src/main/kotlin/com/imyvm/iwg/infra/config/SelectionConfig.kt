package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object SelectionConfig {
    @JvmField
    val SELECTION_MAX_POINTS = Option("core.selection.max_points", 12,
        "the maximum number of points a player can hold in selection mode. Adding a point beyond this limit is rejected.") { obj: Config, path: String? -> positiveInt(path, obj.getInt(path)) }

    @JvmField
    val SELECTION_DISPLAY_LINE_STEP = Option("core.selection.display_line_step", 2,
        "the step in blocks between line-edge particle markers during selection display.") { obj: Config, path: String? -> positiveInt(path, obj.getInt(path)) }

    @JvmField
    val SELECTION_DISPLAY_PILLAR_STEP = Option("core.selection.display_pillar_step", 8,
        "the step in blocks between pillar particle markers during selection display.") { obj: Config, path: String? -> positiveInt(path, obj.getInt(path)) }
}
