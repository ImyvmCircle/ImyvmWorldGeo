package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.text.Text

class GeoShape(
    var geoShapeType: GeoShapeType,
    var shapeParameter: MutableList<Int>
) {

    fun getShapeInfo(): Text? {
        val area = "%.2f".format(calculateArea())

        return when (geoShapeType) {
            GeoShapeType.CIRCLE -> getCircleInfo(area)
            GeoShapeType.RECTANGLE -> getRectangleInfo(area)
            GeoShapeType.POLYGON -> getPolygonInfo(area)
            else -> Translator.tr("geoshape.unknown.info", area)
        }
    }

    fun isInside(x: Int, y: Int): Boolean {
        return when (geoShapeType) {
            GeoShapeType.CIRCLE -> isInsideCircle(x, y, shapeParameter)
            GeoShapeType.RECTANGLE -> isInsideRectangle(x, y, shapeParameter)
            GeoShapeType.POLYGON -> isInsidePolygon(x, y, shapeParameter)
            else -> false
        }
    }

    fun calculateArea(): Double {
        return when (geoShapeType) {
            GeoShapeType.CIRCLE -> calculateCircleArea(this.shapeParameter)
            GeoShapeType.RECTANGLE -> calculateRectangleArea(this.shapeParameter)
            GeoShapeType.POLYGON -> calculatePolygonArea(shapeParameter)
            else -> 0.0
        }
    }

    private fun getCircleInfo(area: String): Text? {
        if (shapeParameter.size < 3) {
            return Translator.tr("geoshape.circle.invalid.info", area)
        }
        return Translator.tr(
            "geoshape.circle.info",
            shapeParameter[0], // centerX
            shapeParameter[1], // centerY
            shapeParameter[2], // radius
            area
        )
    }

    private fun getRectangleInfo(area: String): Text? {
        if (shapeParameter.size < 4) {
            return Translator.tr("geoshape.rectangle.invalid.info", area)
        }
        return Translator.tr(
            "geoshape.rectangle.info",
            shapeParameter[0], // west
            shapeParameter[1], // north
            shapeParameter[2], // east
            shapeParameter[3], // south
            area
        )
    }

    private fun getPolygonInfo(area: String): Text? {
        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) {
            return Translator.tr("geoshape.polygon.invalid.info", area)
        }
        val coords = shapeParameter.chunked(2)
            .joinToString(", ") { "(${it[0]}, ${it[1]})" }
        return Translator.tr("geoshape.polygon.info", coords, area)
    }
}