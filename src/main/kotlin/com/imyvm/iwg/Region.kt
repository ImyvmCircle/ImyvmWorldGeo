package com.imyvm.iwg

class Region {
    var name: String = ""
    var numberID: Int = 0
    lateinit var geometryScope: GeoScope

    companion object{
        class GeoScope
        {
            var scopeId: Int = 0
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

                    //先判断点是否在边上（利用叉积 + 点积范围）
                    val cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
                    if (cross == 0) {
                        if (x in minOf(xi, xj)..maxOf(xi, xj) &&
                            y in minOf(yi, yj)..maxOf(yi, yj)
                        ) {
                            return true
                        }
                    }

                    //射线法
                    val intersect = ((yi > y) != (yj > y)) &&
                            (x < (xj - xi) * (y - yi).toDouble() / (yj - yi) + xi)
                    if (intersect) inside = !inside
                    j = i
                }

                return inside
            }
        }

        enum class GeoShapeType {
            UNKNOWN, CIRCLE, RECTANGLE, POLYGON
        }
    }
}