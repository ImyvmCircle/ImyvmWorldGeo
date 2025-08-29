package com.imyvm.iwg.region

import com.imyvm.iwg.ui.Translator
import net.minecraft.text.Text
import kotlin.math.abs

class Region {
    var name: String = ""
    var numberID: Int = 0
    var geometryScope: MutableList<GeoScope> = mutableListOf()

    fun getShapeInfos(): List<Text> {
        val shapeInfos = mutableListOf<Text>()
        geometryScope.forEachIndexed { index, geoScope ->
            geoScope.geoShape?.let { geoShape ->
                val info = geoShape.getShapeInfo(index)
                shapeInfos.add(info)
            }
        }
        return shapeInfos
    }

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
                        val west = shapeParameter[0]
                        val north = shapeParameter[1]
                        val east = shapeParameter[2]
                        val south = shapeParameter[3]
                        x in west..east && y in north..south
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
                            val west = shapeParameter[0].toDouble()
                            val north = shapeParameter[1].toDouble()
                            val east = shapeParameter[2].toDouble()
                            val south = shapeParameter[3].toDouble()
                            (east - west) * (south - north)
                        }
                    }
                    GeoShapeType.POLYGON -> {
                        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) 0.0
                        else {
                            val vertices = shapeParameter.chunked(2).map { Pair(it[0], it[1]) }
                            var area = 0.0
                            var j = vertices.size - 1
                            for (i in vertices.indices) {
                                area += (vertices[j].first + vertices[i].first).toDouble() *
                                        (vertices[j].second - vertices[i].second).toDouble()
                                j = i
                            }
                            abs(area / 2.0)
                        }
                    }
                    else -> 0.0
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

            fun getShapeInfo(index: Int): Text {
                val area = "%.2f".format(calculateArea())

                return when (geoShapeType) {
                    GeoShapeType.CIRCLE -> {
                        if (shapeParameter.size < 3) {
                            Translator.tr("geoshape.circle.invalid", index, area)
                        } else {
                            Translator.tr(
                                "geoshape.circle",
                                index,
                                shapeParameter[0], // centerX
                                shapeParameter[1], // centerY
                                shapeParameter[2], // radius
                                area
                            )
                        }
                    }
                    GeoShapeType.RECTANGLE -> {
                        if (shapeParameter.size < 4) {
                            Translator.tr("geoshape.rectangle.invalid", index, area)
                        } else {
                            Translator.tr(
                                "geoshape.rectangle",
                                index,
                                shapeParameter[0], // west
                                shapeParameter[1], // north
                                shapeParameter[2], // east
                                shapeParameter[3], // south
                                area
                            )
                        }
                    }
                    GeoShapeType.POLYGON -> {
                        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) {
                            Translator.tr("geoshape.polygon.invalid", index, area)
                        } else {
                            val coords = shapeParameter.chunked(2)
                                .joinToString(", ") { "(${it[0]}, ${it[1]})" }
                            Translator.tr("geoshape.polygon", index, coords, area)
                        }
                    }
                    else -> Translator.tr("geoshape.unknown", index, area)
                }
            }

        }

        enum class GeoShapeType {
            UNKNOWN, CIRCLE, RECTANGLE, POLYGON
        }
    }
}