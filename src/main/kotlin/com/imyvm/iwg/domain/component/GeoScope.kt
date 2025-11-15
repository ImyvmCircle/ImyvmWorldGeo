package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.circleContainsPoint
import com.imyvm.iwg.util.geo.polygonContainsPoint
import com.imyvm.iwg.util.geo.rectangleContainsPoint
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

        private fun iterateCirclePoint(centerX: Int, centerZ: Int, radius: Int): List<Pair<Int, Int>> {
            return generateShapePoints(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius) { x, z ->
                circleContainsPoint(x - centerX, z - centerZ, radius)
            }
        }

        private fun iterateRectanglePoint(x1: Int, z1: Int, x2: Int, z2: Int): List<Pair<Int, Int>> {
            return generateShapePoints(x1, x2, z1, z2) { x, z ->
                rectangleContainsPoint(x, z, x1, x2, z1, z2)
            }
        }

        private fun iteratePolygonPoint(shapeParameters: MutableList<Int>): List<Pair<Int, Int>> {
            val (minX, minZ, maxX, maxZ) = getBoundingBox(shapeParameters)
            val vertices = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until shapeParameters.size step 2) {
                vertices.add(Pair(shapeParameters[i], shapeParameters[i + 1]))
            }

            return generateShapePoints(minX, maxX, minZ, maxZ) { x, z ->
                polygonContainsPoint(x, z, vertices)
            }
        }

        private fun generateShapePoints(minX: Int, maxX: Int, minZ: Int, maxZ: Int, containsPoint: (Int, Int) -> Boolean): List<Pair<Int, Int>> {
            val pointList = mutableListOf<Pair<Int, Int>>()
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (containsPoint(x, z)) {
                        pointList.add(Pair(x, z))
                    }
                }
            }

            val centerX = (minX + maxX) / 2
            val centerZ = (minZ + maxZ) / 2
            return pointList.sortedBy { (x, z) ->
                (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ)
            }
        }

        private fun getBoundingBox(shapeParameters: MutableList<Int>): IntArray {
            val minX = shapeParameters.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0
            val minZ = shapeParameters.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0
            val maxX = shapeParameters.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: 0
            val maxZ = shapeParameters.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: 0
            return intArrayOf(minX, minZ, maxX, maxZ)
        }


        private fun generateSurfacePoint(point: Pair<Int, Int>): BlockPos? {

            return null
        }
    }
}