package com.imyvm.iwg.infra

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

class WorldGeoConfig : HokiConfig("Imyvm_world_geo.conf") {
    companion object{
        @JvmField
        @ConfigOption
        val LANGUAGE = Option(
            "core.language",
            "en_us",
            "the display language of Imyvm World Geo."
        ) { obj: Config, path: String? ->
            obj.getString(
                path
            )
        }

        @JvmField
        @ConfigOption
        val LAZY_TICKER_SECONDS = Option(
            "core.lazy_ticker_seconds",
            1,
            "the interval in seconds for lazy ticker tasks to run."
        ) { obj: Config, path: String? ->
            obj.getInt(
                path
            )
        }

        @JvmField
        @ConfigOption
        val PERMISSION_DEFAULT_BUILD_BREAK = Option(
            "core.permission.build_break.default",
            true,
            "the default build/break permission in regions."
        ) { obj: Config, path: String? ->
            obj.getBoolean(
                path
            )
        }

        @JvmField
        @ConfigOption
        val PERMISSION_DEFAULT_CONTAINER = Option(
            "core.permission.container.default",
            true,
            "the default container interaction permission in regions."
        ) { obj: Config, path: String? ->
            obj.getBoolean(
                path
            )
        }

        @JvmField
        @ConfigOption
        val PERMISSION_DEFAULT_FLY = Option(
            "core.permission.fly.default",
            false,
            "the default fly permission in regions."
        ) { obj: Config, path: String? ->
            obj.getBoolean(
                path
            )
        }

        @JvmField
        @ConfigOption
        val PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS = Option(
            "core.permission.fly.disable_countdown_seconds",
            5,
            "the countdown time in seconds before disabling fly when the player leaves a region that allows flying."
        ) { obj: Config, path: String? ->
            obj.getInt(
                path
            )
        }

        @JvmField
        @ConfigOption
        val PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS = Option(
            "core.permission.fly.disable_fall_immunity_seconds",
            5,
            "the duration in seconds of fall damage immunity after fly is disabled."
        ) { obj: Config, path: String? ->
            obj.getInt(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_RECTANGLE_AREA = Option(
            "core.min_rectangle_area",
            100.0,
            "the minimum area of a rectangle region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_SIDE_LENGTH = Option(
            "core.min_side_length",
            10.0,
            "the minimum length of each side of a rectangle region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_CIRCLE_RADIUS = Option(
            "core.min_circle_radius",
            5.0,
            "the minimum radius of a circle region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_POLYGON_AREA = Option(
            "core.min_polygon_area",
            100.0,
            "the minimum area of a polygon region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_POLYGON_SPAN = Option(
            "core.min_polygon_span",
            10.0,
            "the minimum span (width and height of the bounding box) of a polygon region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_ASPECT_RATIO = Option(
            "core.min_aspect_ratio",
            0.2,
            "the minimum aspect ratio (width / height) of a polygon region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val MIN_EDGE_LENGTH = Option(
            "core.min_edge_length",
            5.0,
            "the minimum length of each edge of a polygon region."
        ) { obj: Config, path: String? ->
            obj.getDouble(
                path
            )
        }

        @JvmField
        @ConfigOption
        val TELEPORT_POINT_FALLBACK_SEARCH_RADIUS = Option(
            "core.teleport_point.fallback_search_radius",
            2,
            "the radius of the cubic search area used to find a safe fallback teleport point when the original is unsafe. A radius of 2 means a 5x5x5 search cube."
        ) { obj: Config, path: String? ->
            obj.getInt(
                path
            )
        }
    }
}