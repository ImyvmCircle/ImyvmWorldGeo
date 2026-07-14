package com.imyvm.iwg.domain.component

import com.imyvm.iwg.util.text.Translator
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.Level

internal const val MAX_TELEPORT_FALLBACK_SEARCH_RADIUS = 8

internal fun requireTeleportFallbackSearchRadius(searchRadius: Int): Int {
    require(searchRadius in 0..MAX_TELEPORT_FALLBACK_SEARCH_RADIUS) {
        "search radius must be between 0 and $MAX_TELEPORT_FALLBACK_SEARCH_RADIUS"
    }
    return searchRadius
}

internal fun findClosestMatchingBlockPos(
    center: BlockPos,
    searchRadius: Int,
    matches: (BlockPos) -> Boolean
): BlockPos? {
    val radius = requireTeleportFallbackSearchRadius(searchRadius)
    return BlockPos.findClosestMatch(center, radius, radius, matches).orElse(null)
}

class GeoShape(
    geoShapeType: GeoShapeType,
    shapeParameter: MutableList<Int>
) {
    private var geometry: ShapeGeometry = ShapeGeometry.from(geoShapeType, shapeParameter)

    internal val typedGeometry: ShapeGeometry
        get() = geometry

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
        return findClosestMatchingBlockPos(center, searchRadius) { candidate ->
            candidate != center && isValidTeleportPoint(world, candidate)
        }
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
