package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

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
                GeoShapeType.CIRCLE -> updateTeleportPointCircle(par)
                GeoShapeType.RECTANGLE -> updateTeleportPointRectangle(par)
                GeoShapeType.POLYGON -> updateTeleportPointPolygon(par)
                GeoShapeType.UNKNOWN -> null
            }
        }

        private fun updateTeleportPointCircle(shapeParameters: MutableList<Int>): BlockPos? {

            return null
        }

        private fun updateTeleportPointRectangle(shapeParameters: MutableList<Int>): BlockPos? {

            return null
        }

        private fun updateTeleportPointPolygon(shapeParameters: MutableList<Int>): BlockPos? {

            return null
        }

        fun certificateTeleportPoint(teleportPoint: BlockPos?): Boolean{

            return true
        }
    }
}