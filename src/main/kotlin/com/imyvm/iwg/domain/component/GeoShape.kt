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

/**
 * Immutable validated shape value.
 *
 * The raw type/parameter constructor is retained for database and addon ABI compatibility.
 * New code should use [circle], [rectangle], or [polygon].
 */
class GeoShape(
    geoShapeType: GeoShapeType,
    shapeParameter: MutableList<Int>
) {
    private val geometry: ShapeGeometry = ShapeGeometry.from(geoShapeType, shapeParameter)

    internal val typedGeometry: ShapeGeometry
        get() = geometry

    @set:Deprecated("GeoShape is immutable; construct a replacement with a named factory")
    var geoShapeType: GeoShapeType
        get() = geometry.type
        set(value) {
            require(value == geometry.type) {
                "shape type cannot be changed without compatible parameters"
            }
        }

    /** Legacy ABI and persistence view. New code should use the named factories. */
    @set:Deprecated("GeoShape is immutable; construct a replacement with a named factory")
    var shapeParameter: MutableList<Int>
        get() = geometry.toLegacyParameters()
        set(value) {
            require(value == geometry.toLegacyParameters()) {
                "shape parameters cannot be changed; construct a replacement GeoShape"
            }
        }

    fun getShapeInfo(): Component? {
        val area = String.format(java.util.Locale.ROOT, "%.2f", calculateArea())

        return when (val current = geometry) {
            is CircleGeometry -> getCircleInfo(current, area)
            is RectangleGeometry -> getRectangleInfo(current, area)
            is PolygonGeometry -> getPolygonInfo(current, area)
            UnknownGeometry -> Translator.tr("geo.shape.unknown.info", area)
        }
    }

    fun containsPoint(x: Int, y: Int): Boolean {
        return geometry.containsPoint(x, y)
    }

    fun calculateArea(): Double = geometry.calculateArea()

    /**
     * Retained for JVM compatibility. Checks one deterministic representative surface position
     * and returns null when that position is unsafe; it does not scan the complete shape.
     */
    fun generateTeleportPoint(world: Level): BlockPos? {
        return geometry.representativePoint()?.let { generateSurfacePoint(world, it) }
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
        )
    }

    private fun getRectangleInfo(rectangle: RectangleGeometry, area: String): Component? {
        return Translator.tr(
            "geo.shape.rectangle.info",
            rectangle.west,
            rectangle.north,
            rectangle.east,
            rectangle.south,
            area
        )
    }

    private fun getPolygonInfo(polygon: PolygonGeometry, area: String): Component? {
        val coords = buildString {
            for (index in 0 until polygon.vertexCount) {
                if (index > 0) append(", ")
                append('(').append(polygon.x(index)).append(", ").append(polygon.z(index)).append(')')
            }
        }
        return Translator.tr("geo.shape.polygon.info", coords, area)
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
        return isValidTeleportPoint(pos, isPhysicalSafe(world, pos))
    }

    internal fun isValidTeleportPoint(pos: BlockPos, physicallySafe: Boolean): Boolean =
        physicallySafe && containsPoint(pos.x, pos.z)

    companion object {
        /** Creates a structurally validated circle. Placement policy is checked by the owning Scope operation. */
        @JvmStatic
        fun circle(center: GeoPoint, radius: Int): GeoShape =
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(center.x, center.z, radius))

        /** Creates a structurally validated rectangle from two order-independent opposite corners. */
        @JvmStatic
        fun rectangle(firstCorner: GeoPoint, oppositeCorner: GeoPoint): GeoShape = GeoShape(
            GeoShapeType.RECTANGLE,
            mutableListOf(
                minOf(firstCorner.x, oppositeCorner.x),
                minOf(firstCorner.z, oppositeCorner.z),
                maxOf(firstCorner.x, oppositeCorner.x),
                maxOf(firstCorner.z, oppositeCorner.z)
            )
        )

        /** Creates a structurally validated polygon and snapshots the supplied vertices. */
        @JvmStatic
        fun polygon(vertices: List<GeoPoint>): GeoShape {
            require(vertices.size >= 3) { "polygon requires at least three vertices" }
            require(isPolygonVertexCountSupported(vertices.size)) {
                "polygon must not exceed $MAX_POLYGON_VERTICES vertices"
            }
            val parameters = ArrayList<Int>(vertices.size * 2)
            for (vertex in vertices) {
                parameters.add(vertex.x)
                parameters.add(vertex.z)
            }
            return GeoShape(GeoShapeType.POLYGON, parameters)
        }

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
