package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.Level

class GeoShape(
    var geoShapeType: GeoShapeType,
    var shapeParameter: MutableList<Int>
) {

    fun getShapeInfo(): Component? {
        val area = "%.2f".format(calculateArea())

        return when (geoShapeType) {
            GeoShapeType.CIRCLE -> getCircleInfo(area)
            GeoShapeType.RECTANGLE -> getRectangleInfo(area)
            GeoShapeType.POLYGON -> getPolygonInfo(area)
            else -> Translator.tr("geo.shape.unknown.info", area)!!
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

    fun generateTeleportPoint(world: Level): BlockPos? {
        val par = this.shapeParameter

        return when (this.geoShapeType) {
            GeoShapeType.CIRCLE -> generateTeleportPointByType(world, par, GeoShapeType.CIRCLE)
            GeoShapeType.RECTANGLE -> generateTeleportPointByType(world, par, GeoShapeType.RECTANGLE)
            GeoShapeType.POLYGON -> generateTeleportPointByType(world, par, GeoShapeType.POLYGON)
            GeoShapeType.UNKNOWN -> null
        }
    }

    fun certificateTeleportPoint(world: Level, pointToTest: BlockPos): Boolean {
        return isValidTeleportPoint(world, pointToTest)
    }

    fun getTeleportPointInvalidReasonKey(world: Level, pos: BlockPos): String? {
        if (!this.containsPoint(pos.x, pos.z)) return "teleport_point.invalid.out_of_scope"
        return getPhysicalSafetyFailureReasonKey(world, pos)
    }

    fun findNearestValidTeleportPoint(world: Level, center: BlockPos, searchRadius: Int): BlockPos? {
        val candidates = mutableListOf<BlockPos>()
        for (dy in -searchRadius..searchRadius) {
            for (dx in -searchRadius..searchRadius) {
                for (dz in -searchRadius..searchRadius) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    candidates.add(BlockPos(center.x + dx, center.y + dy, center.z + dz))
                }
            }
        }
        candidates.sortWith(
            compareBy<BlockPos> { Math.abs(it.y - center.y) }
                .thenBy { (it.x - center.x) * (it.x - center.x) + (it.z - center.z) * (it.z - center.z) }
        )
        return candidates.firstOrNull { isValidTeleportPoint(world, it) }
    }

    private fun getCircleInfo(area: String): Component? {
        if (shapeParameter.size < 3) {
            return Translator.tr("geo.shape.circle.invalid.info", area)!!
        }
        return Translator.tr(
            "geo.shape.circle.info",
            shapeParameter[0], // centerX
            shapeParameter[1], // centerZ
            shapeParameter[2], // radius
            area
        )!!
    }

    private fun getRectangleInfo(area: String): Component? {
        if (shapeParameter.size < 4) {
            return Translator.tr("geo.shape.rectangle.invalid.info", area)!!
        }
        return Translator.tr(
            "geo.shape.rectangle.info",
            shapeParameter[0], // west
            shapeParameter[1], // north
            shapeParameter[2], // east
            shapeParameter[3], // south
            area
        )!!
    }

    private fun getPolygonInfo(area: String): Component? {
        if (shapeParameter.size < 6 || shapeParameter.size % 2 != 0) {
            return Translator.tr("geo.shape.polygon.invalid.info", area)!!
        }
        val coords = shapeParameter.chunked(2)
            .joinToString(", ") { "(${it[0]}, ${it[1]})" }
        return Translator.tr("geo.shape.polygon.info", coords, area)!!
    }

    private fun generateTeleportPointByType(
        world: Level,
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

    private fun generateSurfacePoint(world: Level, point: Pair<Int, Int>): BlockPos? {
        val x = point.first
        val z = point.second
        val topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        val candidatePos = BlockPos(x, topY, z)

        if (isValidTeleportPoint(world, candidatePos)) {
            return candidatePos
        }
        return null
    }

    private fun isValidTeleportPoint(world: Level, pos: BlockPos): Boolean {
        if (!isPhysicalSafe(world, pos)) return false
        return this.containsPoint(pos.x, pos.z)
    }

    companion object {
        fun isPhysicalSafe(world: Level, pos: BlockPos): Boolean {
            val feetState = world.getBlockState(pos)
            val headState = world.getBlockState(pos.above())
            val groundState = world.getBlockState(pos.below())

            val context = CollisionContext.empty()

            if (!feetState.fluidState.isEmpty || !headState.fluidState.isEmpty) {
                return false
            }

            if (!feetState.getCollisionShape(world, pos, context).isEmpty ||
                !headState.getCollisionShape(world, pos.above(), context).isEmpty
            ) {
                return false
            }

            return groundState.isFaceSturdy(world, pos.below(), Direction.UP)
        }

        fun getPhysicalSafetyFailureReasonKey(world: Level, pos: BlockPos): String? {
            val feetState = world.getBlockState(pos)
            val headState = world.getBlockState(pos.above())
            val groundState = world.getBlockState(pos.below())
            val context = CollisionContext.empty()

            if (!feetState.fluidState.isEmpty || !headState.fluidState.isEmpty) {
                return "teleport_point.safety.liquid"
            }
            if (!feetState.getCollisionShape(world, pos, context).isEmpty) {
                return "teleport_point.safety.feet_blocked"
            }
            if (!headState.getCollisionShape(world, pos.above(), context).isEmpty) {
                return "teleport_point.safety.head_blocked"
            }
            if (!groundState.isFaceSturdy(world, pos.below(), Direction.UP)) {
                return "teleport_point.safety.no_ground"
            }
            return null
        }
    }
}