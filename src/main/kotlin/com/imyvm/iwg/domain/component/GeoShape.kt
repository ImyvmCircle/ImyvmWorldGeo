package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.geo.distanceOrderedGrid
import com.imyvm.iwg.util.text.Translator
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.Level

class GeoShape(
    geoShapeType: GeoShapeType,
    shapeParameter: MutableList<Int>
) {
    private var geometry: ShapeGeometry = ShapeGeometry.from(geoShapeType, shapeParameter)

    var geoShapeType: GeoShapeType
        get() = geometry.type
        set(value) {
            require(value == geometry.type) {
                "shape type cannot be changed without compatible parameters"
            }
        }

    var shapeParameter: MutableList<Int>
        get() = geometry.toLegacyParameters()
        set(value) {
            geometry = ShapeGeometry.from(geometry.type, value)
        }

    fun getShapeInfo(): Component? {
        val area = "%.2f".format(calculateArea())

        return when (val current = geometry) {
            is CircleGeometry -> getCircleInfo(current, area)
            is RectangleGeometry -> getRectangleInfo(current, area)
            is PolygonGeometry -> getPolygonInfo(current, area)
            UnknownGeometry -> Translator.tr("geo.shape.unknown.info", area)!!
        }
    }

    fun containsPoint(x: Int, y: Int): Boolean {
        return geometry.containsPoint(x, y)
    }

    fun calculateArea(): Double = geometry.calculateArea()

    fun generateTeleportPoint(world: Level): BlockPos? {
        return generateTeleportPointByType(world)
    }

    fun certificateTeleportPoint(world: Level, pointToTest: BlockPos): Boolean {
        return isValidTeleportPoint(world, pointToTest)
    }

    fun getTeleportPointInvalidReasonKey(world: Level, pos: BlockPos): String? {
        if (!this.containsPoint(pos.x, pos.z)) return "teleport_point.invalid.out_of_scope"
        return getPhysicalSafetyFailureReasonKey(world, pos)
    }

    fun findNearestValidTeleportPoint(world: Level, center: BlockPos, searchRadius: Int): BlockPos? {
        require(searchRadius >= 0) { "search radius must not be negative" }
        val minX = (center.x.toLong() - searchRadius).coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()
        val maxX = (center.x.toLong() + searchRadius).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val minZ = (center.z.toLong() - searchRadius).coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()
        val maxZ = (center.z.toLong() + searchRadius).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val horizontal = distanceOrderedGrid(center.x, center.z, minX, maxX, minZ, maxZ)
        for (verticalDistance in 0..searchRadius) {
            val offsets = if (verticalDistance == 0) intArrayOf(0) else intArrayOf(-verticalDistance, verticalDistance)
            for (dy in offsets) {
                val y = center.y.toLong() + dy
                if (y !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) continue
                for ((x, z) in horizontal) {
                    if (x == center.x && y.toInt() == center.y && z == center.z) continue
                    val candidate = BlockPos(x, y.toInt(), z)
                    if (isValidTeleportPoint(world, candidate)) return candidate
                }
            }
        }
        return null
    }

    fun validateParameters() {
        // Construction and the compatibility setter replace the complete validated geometry atomically.
    }

    private fun getCircleInfo(circle: CircleGeometry, area: String): Component? {
        return Translator.tr(
            "geo.shape.circle.info",
            circle.centerX,
            circle.centerZ,
            circle.radius,
            area
        )!!
    }

    private fun getRectangleInfo(rectangle: RectangleGeometry, area: String): Component? {
        return Translator.tr(
            "geo.shape.rectangle.info",
            rectangle.west,
            rectangle.north,
            rectangle.east,
            rectangle.south,
            area
        )!!
    }

    private fun getPolygonInfo(polygon: PolygonGeometry, area: String): Component? {
        val coords = buildString {
            for (index in 0 until polygon.vertexCount) {
                if (index > 0) append(", ")
                append('(').append(polygon.x(index)).append(", ").append(polygon.z(index)).append(')')
            }
        }
        return Translator.tr("geo.shape.polygon.info", coords, area)!!
    }

    private fun generateTeleportPointByType(world: Level): BlockPos? {
        for (point in geometry.pointSequence()) {
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
