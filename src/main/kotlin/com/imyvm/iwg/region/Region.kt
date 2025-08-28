package com.imyvm.iwg.region

import com.imyvm.iwg.ui.Translator
import net.minecraft.text.Text
import kotlin.math.abs

class Region {
    var name: String = ""
    var numberID: Int = 0
    var geometryScope: MutableList<GeoScope> = mutableListOf()

    companion object {
        class GeoScope {
            var scopeName: String = ""
            var geoShape: GeoShape? = null
        }

        class GeoShape {
            var geoShapeType: GeoShapeType = GeoShapeType.UNKNOWN
            var shapeParameter: MutableList<Int> = mutableListOf()

            fun isInside(x: Int, y: Int): Boolean {
                return when (geoShapeType) {
                    GeoShapeType.CIRCLE -> {
                        if (shapeParameter.size < 3) return false
                        val centerX = shapeParameter[0]
                        val centerY = shapeParameter[1]
                        val radius = shapeParameter[2]
                        val dx = x - centerX
                        val dy = y - centerY
                        dx * dx + dy * dy <= radius * radius
                    }
                    GeoShapeType.RECTANGLE -> {
                        if (shapeParameter.size < 4) return false
                        val left = shapeParameter[0]
                        val top = shapeParameter[1]
                        val right = shapeParameter[2]
                        val bottom = shapeParameter[3]
                        x in left..right && y in top..bottom
                    }
                    GeoShapeType.POLYGON -> {
                        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) return false
                        polygonContainsPoint(
                            x, y,
                            shapeParameter.chunked(2).map { Pair(it[0], it[1]) }
                        )
                    }
                    else -> false
                }
            }

            private fun polygonContainsPoint(x: Int, y: Int, vertices: List<Pair<Int, Int>>): Boolean {
                var inside = false
                var j = vertices.size - 1

                for (i in vertices.indices) {
                    val xi = vertices[i].first
                    val yi = vertices[i].second
                    val xj = vertices[j].first
                    val yj = vertices[j].second

                    val cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
                    if (cross == 0) {
                        if (x in minOf(xi, xj)..maxOf(xi, xj) &&
                            y in minOf(yi, yj)..maxOf(yi, yj)
                        ) {
                            return true
                        }
                    }

                    val intersect = ((yi > y) != (yj > y)) &&
                            (x < (xj - xi) * (y - yi).toDouble() / (yj - yi) + xi)
                    if (intersect) inside = !inside
                    j = i
                }

                return inside
            }

            fun calculateArea(): Double {
                return when (geoShapeType) {
                    GeoShapeType.CIRCLE -> {
                        if (shapeParameter.size < 3) 0.0
                        else {
                            val radius = shapeParameter[2].toDouble()
                            Math.PI * radius * radius
                        }
                    }
                    GeoShapeType.RECTANGLE -> {
                        if (shapeParameter.size < 4) 0.0
                        else {
                            val left = shapeParameter[0].toDouble()
                            val top = shapeParameter[1].toDouble()
                            val right = shapeParameter[2].toDouble()
                            val bottom = shapeParameter[3].toDouble()
                            abs((right - left) * (bottom - top))
                        }
                    }
                    GeoShapeType.POLYGON -> {
                        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) 0.0
                        else {
                            val vertices = shapeParameter.chunked(2).map { Pair(it[0], it[1]) }
                            var area = 0.0
                            var j = vertices.size - 1
                            for (i in vertices.indices) {
                                area += (vertices[j].first + vertices[i].first).toDouble() * (vertices[j].second - vertices[i].second).toDouble()
                                j = i
                            }
                            Math.abs(area / 2.0)
                        }
                    }
                    else -> 0.0
                }
            }

            fun getShapeInfo(): Text {
                val coordinates = shapeParameter.chunked(2).joinToString(", ") { "(${it[0]}, ${it[1]})" }
                val area = "%.2f".format(calculateArea())
                return Translator.tr("geoshape.info", geoShapeType, coordinates, area)
            }
        }

        enum class GeoShapeType {
            UNKNOWN, CIRCLE, RECTANGLE, POLYGON
        }
    }
}