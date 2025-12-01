package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.block.CarpetBlock
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World

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

    fun containsPoint(x: Int, y: Int): Boolean {
        return when (geoShapeType) {
            GeoShapeType.CIRCLE -> circleContainsPoint(x, y, shapeParameter)
            GeoShapeType.RECTANGLE -> rectangleContainsPoint(x, y, shapeParameter)
            GeoShapeType.POLYGON -> polygonContainsPoint(x, y, shapeParameter)
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

    fun generateTeleportPoint(world: World): BlockPos? {
        val par = this.shapeParameter

        return when (this.geoShapeType) {
            GeoShapeType.CIRCLE -> generateTeleportPointByType(world, par, GeoShapeType.CIRCLE)
            GeoShapeType.RECTANGLE -> generateTeleportPointByType(world, par, GeoShapeType.RECTANGLE)
            GeoShapeType.POLYGON -> generateTeleportPointByType(world, par, GeoShapeType.POLYGON)
            GeoShapeType.UNKNOWN -> null
        }
    }

    fun certificateTeleportPointByShape(world: World, pos: BlockPos): Boolean {
        if (!isPhysicalSafe(world, pos)) return false
        return this.containsPoint(pos.x, pos.z)
    }

    private fun getCircleInfo(area: String): Text? {
        if (shapeParameter.size < 3) {
            return Translator.tr("geoshape.circle.invalid.info", area)
        }
        return Translator.tr(
            "geoshape.circle.info",
            shapeParameter[0], // centerX
            shapeParameter[1], // centerZ
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

    private fun generateTeleportPointByType(
        world: World,
        shapeParameters: MutableList<Int>,
        geoShapeType: GeoShapeType
    ): BlockPos? {
        val points = when (geoShapeType) {
            GeoShapeType.CIRCLE -> iterateCirclePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2])
            GeoShapeType.RECTANGLE -> iterateRectanglePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2], shapeParameters[3])
            GeoShapeType.POLYGON -> iteratePolygonPoint(shapeParameters)
            GeoShapeType.UNKNOWN -> return null
        }

        for (point in points) {
            val blockPos = generateSurfacePoint(world, point)
            if (blockPos != null) return blockPos
        }
        return null
    }

    private fun generateSurfacePoint(world: World, point: Pair<Int, Int>): BlockPos? {
        val x = point.first
        val z = point.second
        val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
        val candidatePos = BlockPos(x, topY, z)

        if (certificateTeleportPointByShape(world, candidatePos)) {
            return candidatePos
        }
        return null
    }

    companion object {
        fun isPhysicalSafe(world: World, pos: BlockPos): Boolean {
            val feetState = world.getBlockState(pos)
            val headState = world.getBlockState(pos.up())
            val groundState = world.getBlockState(pos.down())

            if (!feetState.isAir || !headState.isAir) return false

            val isSolid = groundState.hasSolidTopSurface(world, pos.down(), null)
            val isCarpet = groundState.block is CarpetBlock

            return isSolid || isCarpet
        }
    }
}