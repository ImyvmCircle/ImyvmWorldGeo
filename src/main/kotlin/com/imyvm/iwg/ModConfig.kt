package com.imyvm.iwg

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

class ModConfig : HokiConfig("Imyvm_world_geo.conf") {
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
    }
}