package com.imyvm.iwg.domain.component

import com.imyvm.iwg.application.interaction.buildShapeInfoLine
import com.imyvm.iwg.application.region.findNearestValidShapeTeleportPoint
import com.imyvm.iwg.application.region.generateRepresentativeSurfacePoint
import com.imyvm.iwg.application.region.isPhysicallySafeTeleportPosition
import com.imyvm.iwg.application.region.isValidShapeTeleportPoint
import com.imyvm.iwg.application.region.physicalTeleportSafetyFailureReasonKey
import com.imyvm.iwg.application.region.shapeTeleportPointInvalidReasonKey
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

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

    /**
     * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
     */
    @Deprecated("Use PlayerInteractionApi.queryRegionInfo or RegionDataApi structured queries")
    fun getShapeInfo(): Component? = buildShapeInfoLine(this)

    fun containsPoint(x: Int, y: Int): Boolean {
        return geometry.containsPoint(x, y)
    }

    fun calculateArea(): Double = geometry.calculateArea()

    /**
     * Retained for JVM compatibility. Checks one deterministic representative surface position
     * and returns null when that position is unsafe; it does not scan the complete shape.
     *
     * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
     */
    @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
    fun generateTeleportPoint(world: Level): BlockPos? =
        generateRepresentativeSurfacePoint(this, world)

    /**
     * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
     */
    @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
    fun certificateTeleportPoint(world: Level, pointToTest: BlockPos): Boolean =
        isValidShapeTeleportPoint(this, world, pointToTest)

    /**
     * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
     */
    @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
    fun getTeleportPointInvalidReasonKey(world: Level, pos: BlockPos): String? =
        shapeTeleportPointInvalidReasonKey(this, world, pos)

    /**
     * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
     */
    @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
    fun findNearestValidTeleportPoint(world: Level, center: BlockPos, searchRadius: Int): BlockPos? =
        findNearestValidShapeTeleportPoint(this, world, center, searchRadius)

    fun validateParameters() {
        // Construction and the compatibility setter replace the complete validated geometry atomically.
    }

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

        /**
         * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
         */
        @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
        fun isPhysicalSafe(world: Level, pos: BlockPos): Boolean =
            isPhysicallySafeTeleportPosition(world, pos)

        /**
         * Compatibility-only reverse-layer delegate. New code must use the application/API boundary.
         */
        @Deprecated("Use owner-explicit PlayerInteractionApi teleport operations")
        fun getPhysicalSafetyFailureReasonKey(world: Level, pos: BlockPos): String? =
            physicalTeleportSafetyFailureReasonKey(world, pos)
    }
}
