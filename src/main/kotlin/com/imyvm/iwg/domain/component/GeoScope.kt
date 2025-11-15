package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import kotlin.math.pow
import kotlin.math.sqrt

class GeoScope(
    var scopeName: String,
    var teleportPoint: BlockPos?,
    var geoShape: GeoShape?,
    var settings: MutableList<Setting> = mutableListOf()
) {

    fun getScopeInfo(index: Int): Text? {
        val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
        return Translator.tr("scope.info", index, scopeName, shapeInfoString)
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return Region.formatSettings(server, settings, "scope.setting", scopeName)
    }

    companion object {
        fun updateTeleportPoint(geoShape: GeoShape): BlockPos?{
            val par = geoShape.shapeParameter
            return when (geoShape.geoShapeType) {
                GeoShapeType.CIRCLE -> updateTeleportPoint(par, GeoShapeType.CIRCLE)
                GeoShapeType.RECTANGLE -> updateTeleportPoint(par, GeoShapeType.RECTANGLE)
                GeoShapeType.POLYGON -> updateTeleportPoint(par, GeoShapeType.POLYGON)
                GeoShapeType.UNKNOWN -> null
            }
        }

        fun certificateTeleportPoint(teleportPoint: BlockPos?): Boolean{

            return true
        }

        private fun updateTeleportPoint(shapeParameters: MutableList<Int>, geoShapeType: GeoShapeType): BlockPos? {
            val points = when (geoShapeType) {
                GeoShapeType.CIRCLE -> iterateCirclePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2])
                GeoShapeType.RECTANGLE -> iterateRectanglePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2], shapeParameters[3])
                GeoShapeType.POLYGON -> iteratePolygonPoint(shapeParameters)
                GeoShapeType.UNKNOWN -> return null
            }
            for (point in points) {
                val blockPos = generateSurfacePoint(point)
                if (blockPos != null) return blockPos
            }
            return null
        }


        private fun generateSurfacePoint(point: Pair<Int, Int>): BlockPos? {

            return null
        }
    }
}