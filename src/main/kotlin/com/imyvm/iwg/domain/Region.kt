package com.imyvm.iwg.domain

import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class Region(
    var name: String,
    var numberID: Int,
    var geometryScope: MutableList<GeoScope>,
    var settings: MutableList<Setting> = mutableListOf()
) {
    fun getScopeByName(scopeName: String): GeoScope {
        return geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw IllegalArgumentException(Translator.tr("region.error.no_scope", scopeName, name)!!.string)
    }

    fun getScopeInfos(server: MinecraftServer): List<Text> {
        val infos = mutableListOf<Text>()
        geometryScope.forEachIndexed { index, geoScope ->
            geoScope.getScopeInfo(index)?.let { infos.add(it) }
            infos.addAll(geoScope.getSettingInfos(server))
        }
        return infos
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return formatSettings(server, settings, "region.setting")
    }

    fun calculateTotalArea(): Double {
        var totalArea = 0.0
        for (scope in geometryScope) {
            scope.geoShape?.let {
                totalArea += it.calculateArea()
            }
        }
        return "%.2f".format(totalArea).toDouble()
    }

    companion object {
        class GeoScope(
            var scopeName: String,
            var geoShape: GeoShape?,
            var settings: MutableList<Setting> = mutableListOf()
        ) {

            fun getScopeInfo(index: Int): Text? {
                val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
                return Translator.tr("scope.info", index, scopeName, shapeInfoString)
            }

            fun getSettingInfos(server: MinecraftServer): List<Text> {
                return formatSettings(server, settings, "scope.setting", scopeName)
            }
        }

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
                    GeoShapeType.POLYGON -> calculatePolygonArea(this.shapeParameter)
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

        enum class GeoShapeType {
            UNKNOWN,
            CIRCLE,
            RECTANGLE,
            POLYGON
        }

        private fun formatSettings(
            server: MinecraftServer,
            settings: List<Setting>,
            key: String,
            scopeName: String? = null
        ): List<Text> {
            val personalSettings = mutableListOf<String>()
            val globalSettings = mutableListOf<String>()

            settings.forEach { s ->
                val display = if (s.isPersonal) {
                    val playerName = resolvePlayerName(server, s.playerUUID)
                    "${s.key}=${s.value} (Player $playerName)"
                } else {
                    "${s.key}=${s.value}"
                }
                if (s.isPersonal) personalSettings.add(display)
                else globalSettings.add(display)
            }

            val result = mutableListOf<Text>()

            if (globalSettings.isNotEmpty()) {
                val combined = globalSettings.joinToString(", ")
                val text = if (scopeName == null) {
                    Translator.tr(key, combined)
                } else {
                    Translator.tr(key, scopeName, combined)
                }
                text?.let { result.add(it) }
            }

            if (personalSettings.isNotEmpty()) {
                val combined = personalSettings.joinToString(", ")
                val text = if (scopeName == null) {
                    Translator.tr("${key}.personal", combined)
                } else {
                    Translator.tr("${key}.personal", scopeName, combined)
                }
                text?.let { result.add(it) }
            }

            return result
        }
    }
}