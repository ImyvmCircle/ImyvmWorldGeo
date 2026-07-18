package com.imyvm.iwg.application.selection

import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.component.ShapeGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import com.imyvm.iwg.infra.config.GeoConfig
import com.imyvm.iwg.util.geo.checkPolygonSize
import com.imyvm.iwg.util.geo.findNearestAdjacentPoints
import com.imyvm.iwg.util.geo.isConvex
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.domain.component.MAX_POLYGON_VERTICES
import com.imyvm.iwg.application.selection.display.evaluateModifyPolygonExplicitInsert
import com.imyvm.iwg.util.text.TextParser
import com.imyvm.iwg.util.text.Translator
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.hypot

private const val MAX_DISPLAYED_EXISTING_POINTS = 8
internal const val MAX_DISPLAYED_SELECTION_POINTS = 12

fun buildPointAddedMessage(state: SelectionState, addedPos: BlockPos): Component {
    val msg = StringBuilder()
    val shape = state.hypotheticalShape
    if (shape is HypotheticalShape.ModifyExisting) {
        buildModifyMessage(msg, state, addedPos, shape.scope, isUndo = false)
    } else {
        buildNormalMessage(msg, state, addedPos, isUndo = false)
    }
    return TextParser.parse(msg.toString())
}

fun buildPointUndoMessage(state: SelectionState, removedPos: BlockPos): Component {
    val msg = StringBuilder()
    val shape = state.hypotheticalShape
    if (shape is HypotheticalShape.ModifyExisting) {
        buildModifyMessage(msg, state, removedPos, shape.scope, isUndo = true)
    } else {
        buildNormalMessage(msg, state, removedPos, isUndo = true)
    }
    return TextParser.parse(msg.toString())
}

fun buildModifyStartMessage(scope: GeoScope): Component {
    val msg = StringBuilder()
    val geometry = scope.geoShape?.typedGeometry ?: UnknownGeometry
    val oldPoints = extractScopePoints(geometry)
    val shapeType = geometry.type
    msg.append(Translator.raw("selection.feedback.separator"))
    msg.append("\n")
    msg.append(Translator.raw("interaction.meta.select.start.modify_scope", scope.scopeName, shapeType.name))
    msg.append("\n")
    appendExistingScopePoints(msg, scope.scopeName, shapeType, oldPoints)
    msg.append("\n")
    appendModifyShapeParameterBlock(msg, shapeType, scope)
    msg.append("\n")
    appendModifyGuidance(msg, geometry, emptyList(), scope.scopeName, oldPoints)
    msg.append("\n")
    msg.append(Translator.raw("selection.feedback.separator"))
    return TextParser.parse(msg.toString())
}

fun formatXZOnly(pos: BlockPos): String = "(${pos.x},${pos.z})"

private fun buildNormalMessage(msg: StringBuilder, state: SelectionState, eventPos: BlockPos, isUndo: Boolean) {
    val points = state.points
    val count = points.size
    val effectiveShape = state.getEffectiveShapeType()
    val isAuto = state.hypotheticalShape == null

    msg.append(Translator.raw("selection.feedback.separator"))
    msg.append("\n")
    msg.append(Translator.raw("selection.feedback.all_points_header"))
    if (points.isEmpty()) {
        msg.append("\n")
        msg.append(Translator.raw("selection.feedback.none"))
    } else {
        for ((idx, pt) in points.take(MAX_DISPLAYED_SELECTION_POINTS).withIndex()) {
            val role = getNormalPointRole(effectiveShape, isAuto, idx, count)
            msg.append("\n")
            msg.append(Translator.raw("selection.feedback.point_line", pt.x, pt.z, role))
        }
        appendOmittedSelectionPointCount(msg, points.size)
    }

    msg.append("\n")
    appendShapeParameterBlock(msg, effectiveShape, isAuto)
    msg.append("\n")
    appendNormalGuidance(msg, effectiveShape, isAuto, count)

    val warning = buildNormalValidationWarning(effectiveShape, isAuto, points)
    if (warning.isNotEmpty()) {
        msg.append("\n")
        msg.append(warning)
    }

    val particleNote = buildNormalParticleNote(effectiveShape, isAuto, points)
    if (particleNote.isNotEmpty()) {
        msg.append("\n")
        msg.append(particleNote)
    }

    msg.append("\n")
    if (isUndo) {
        msg.append(Translator.raw("selection.feedback.header_undo", eventPos.x, eventPos.z))
    } else {
        val pointIndex = count - 1
        val roleRaw = getNormalPointRole(effectiveShape, isAuto, pointIndex, count)
        msg.append(Translator.raw("selection.feedback.header_added", eventPos.x, eventPos.z, roleRaw))
    }
    msg.append("\n")
    msg.append(Translator.raw("selection.feedback.separator"))
}

private fun getNormalPointRole(shape: GeoShapeType, isAuto: Boolean, idx: Int, total: Int): String {
    if (isAuto) {
        return when {
            total <= 2 && idx == 0 -> Translator.raw("selection.feedback.role.auto_corner1")
            total <= 2 && idx == 1 -> Translator.raw("selection.feedback.role.auto_corner2")
            else -> Translator.raw("selection.feedback.role.auto_polygon_vertex", idx + 1)
        }
    }
    return when (shape) {
        GeoShapeType.CIRCLE -> when {
            idx == 0 -> Translator.raw("selection.feedback.role.circle_center")
            idx == 1 -> Translator.raw("selection.feedback.role.circle_circumference")
            else -> Translator.raw("selection.feedback.role.circle_excess")
        }
        GeoShapeType.RECTANGLE -> when {
            idx == 0 -> Translator.raw("selection.feedback.role.rect_corner1")
            idx == 1 -> Translator.raw("selection.feedback.role.rect_corner2")
            else -> Translator.raw("selection.feedback.role.rect_excess")
        }
        GeoShapeType.POLYGON -> Translator.raw("selection.feedback.role.polygon_vertex", idx + 1)
        else -> ""
    }
}

private fun appendNormalGuidance(msg: StringBuilder, shape: GeoShapeType, isAuto: Boolean, count: Int) {
    val guidanceRaw = when {
        !isAuto && shape == GeoShapeType.CIRCLE -> when {
            count < 2 -> Translator.raw(
                "selection.feedback.guidance.circle.need_circumference",
                GeoConfig.MIN_CIRCLE_RADIUS.value.toInt()
            )
            count == 2 -> Translator.raw("selection.feedback.guidance.circle.complete")
            else -> Translator.raw("selection.feedback.guidance.circle.excess")
        }
        !isAuto && shape == GeoShapeType.RECTANGLE -> when {
            count < 2 -> Translator.raw(
                "selection.feedback.guidance.rect.need_corner2",
                GeoConfig.MIN_SIDE_LENGTH.value.toInt(),
                GeoConfig.MIN_RECTANGLE_AREA.value.toInt()
            )
            count == 2 -> Translator.raw("selection.feedback.guidance.rect.complete")
            else -> Translator.raw("selection.feedback.guidance.rect.excess")
        }
        !isAuto && shape == GeoShapeType.POLYGON -> when {
            count < 3 -> Translator.raw(
                "selection.feedback.guidance.polygon.need_more",
                3 - count,
                GeoConfig.MIN_EDGE_LENGTH.value.toInt(),
                GeoConfig.MIN_POLYGON_SPAN.value.toInt()
            )
            else -> Translator.raw("selection.feedback.guidance.polygon.can_finalize", count)
        }
        isAuto && count == 1 -> Translator.raw("selection.feedback.guidance.auto.first_point")
        isAuto && count == 2 -> Translator.raw("selection.feedback.guidance.auto.rect_ready")
        isAuto -> Translator.raw("selection.feedback.guidance.auto.polygon_ready", count)
        else -> ""
    }
    if (guidanceRaw.isNotEmpty()) msg.append(guidanceRaw)
}

private fun buildNormalValidationWarning(shape: GeoShapeType, isAuto: Boolean, points: List<BlockPos>): String {
    val effectiveShape = if (isAuto && points.size > 2) GeoShapeType.POLYGON else shape
    return when (effectiveShape) {
        GeoShapeType.RECTANGLE -> validateRectanglePoints(points)
        GeoShapeType.CIRCLE -> validateCirclePoints(points)
        GeoShapeType.POLYGON -> validatePolygonPoints(points)
        else -> ""
    }
}

private fun validateRectanglePoints(points: List<BlockPos>): String {
    if (points.size < 2) return ""
    val p1 = points[0]; val p2 = points[1]
    return when {
        p1.x == p2.x && p1.z == p2.z ->
            Translator.raw("selection.feedback.warn.rect.same_point")
        p1.x == p2.x ->
            Translator.raw("selection.feedback.warn.rect.coincident_x", p1.x)
        p1.z == p2.z ->
            Translator.raw("selection.feedback.warn.rect.coincident_z", p1.z)
        else -> {
            val w = abs(p1.x.toLong() - p2.x); val h = abs(p1.z.toLong() - p2.z); val area = w.toDouble() * h
            val minSide = GeoConfig.MIN_SIDE_LENGTH.value; val minArea = GeoConfig.MIN_RECTANGLE_AREA.value
            val minAspect = GeoConfig.MIN_ASPECT_RATIO.value
            val aspect = if (h == 0L) Double.MAX_VALUE else w.toDouble() / h
            when {
                area < minArea || w < minSide || h < minSide ->
                    Translator.raw(
                        "selection.feedback.warn.rect.too_small",
                        w, h, area, minSide.toInt(), minArea.toInt()
                    )
                aspect < minAspect || aspect > 1.0 / minAspect ->
                    Translator.raw(
                        "selection.feedback.warn.rect.aspect_ratio",
                        String.format(java.util.Locale.ROOT, "%.2f", aspect),
                        String.format(java.util.Locale.ROOT, "%.2f", minAspect),
                        String.format(java.util.Locale.ROOT, "%.2f", 1.0 / minAspect)
                    )
                else -> ""
            }
        }
    }
}

private fun validateCirclePoints(points: List<BlockPos>): String {
    if (points.size < 2) return ""
    val c = points[0]; val p = points[1]
    if (c.x == p.x && c.z == p.z) return Translator.raw("selection.feedback.warn.circle.same_point")
    val radius = hypot(p.x.toDouble() - c.x, p.z.toDouble() - c.z)
    val minRadius = GeoConfig.MIN_CIRCLE_RADIUS.value
    return if (radius < minRadius)
        Translator.raw(
            "selection.feedback.warn.circle.too_small",
            String.format(java.util.Locale.ROOT, "%.1f", radius),
            minRadius.toInt()
        )
    else ""
}

private fun validatePolygonPoints(points: List<BlockPos>): String {
    if (points.size < 3) return ""
    if (!isPolygonVertexCountSupported(points.size)) {
        return Translator.raw("error.polygon_vertex_limit_exceeded", MAX_POLYGON_VERTICES)
    }
    if (points.distinct().size != points.size) return Translator.raw("selection.feedback.warn.polygon.duplicate")
    if (!isConvex(points)) return Translator.raw("selection.feedback.warn.polygon.not_convex")
    return when (checkPolygonSize(points)) {
        CreationError.UnderSizeLimit ->
            Translator.raw("selection.feedback.warn.polygon.under_area", GeoConfig.MIN_POLYGON_AREA.value.toInt())
        CreationError.UnderBoundingBoxLimit ->
            Translator.raw("selection.feedback.warn.polygon.under_span", GeoConfig.MIN_POLYGON_SPAN.value.toInt())
        CreationError.EdgeTooShort ->
            Translator.raw("selection.feedback.warn.polygon.edge_too_short", GeoConfig.MIN_EDGE_LENGTH.value.toInt())
        CreationError.AspectRatioInvalid ->
            Translator.raw(
                "selection.feedback.warn.polygon.aspect_ratio",
                String.format(java.util.Locale.ROOT, "%.2f", GeoConfig.MIN_ASPECT_RATIO.value),
                String.format(java.util.Locale.ROOT, "%.2f", 1.0 / GeoConfig.MIN_ASPECT_RATIO.value)
            )
        CreationError.PolygonVertexLimitExceeded ->
            Translator.raw("error.polygon_vertex_limit_exceeded", MAX_POLYGON_VERTICES)
        else -> ""
    }
}

private fun buildModifyMessage(msg: StringBuilder, state: SelectionState, eventPos: BlockPos, scope: GeoScope, isUndo: Boolean) {
    val geometry = scope.geoShape?.typedGeometry ?: UnknownGeometry
    val oldPoints = extractScopePoints(geometry)
    val shapeType = geometry.type
    val newPoints = state.points

    msg.append(Translator.raw("selection.feedback.separator"))
    msg.append("\n")
    appendExistingScopePoints(msg, scope.scopeName, shapeType, oldPoints)

    msg.append("\n")
    msg.append(Translator.raw("selection.feedback.modify.new_header"))
    if (newPoints.isEmpty()) {
        msg.append("\n")
        msg.append(Translator.raw("selection.feedback.none"))
    } else {
        for ((idx, pt) in newPoints.take(MAX_DISPLAYED_SELECTION_POINTS).withIndex()) {
            val role = getModifyNewPointRole(geometry, newPoints, idx, oldPoints)
            msg.append("\n")
            msg.append(Translator.raw("selection.feedback.point_line", pt.x, pt.z, role))
        }
        appendOmittedSelectionPointCount(msg, newPoints.size)
    }

    msg.append("\n")
    appendModifyShapeParameterBlock(msg, shapeType, scope)
    msg.append("\n")
    appendModifyGuidance(msg, geometry, newPoints, scope.scopeName, oldPoints)

    val particleNote = Translator.raw("selection.feedback.particles.modify")
    if (particleNote.isNotEmpty()) {
        msg.append("\n")
        msg.append(particleNote)
    }

    msg.append("\n")
    if (isUndo) {
        msg.append(Translator.raw("selection.feedback.header_undo", eventPos.x, eventPos.z))
    } else {
        val pointIndex = newPoints.size - 1
        val roleRaw = getModifyNewPointRole(geometry, newPoints, pointIndex, oldPoints)
        msg.append(Translator.raw("selection.feedback.header_added", eventPos.x, eventPos.z, roleRaw))
    }
    msg.append("\n")
    msg.append(Translator.raw("selection.feedback.separator"))
}

private fun appendOmittedSelectionPointCount(msg: StringBuilder, total: Int) {
    val omitted = total - MAX_DISPLAYED_SELECTION_POINTS
    if (omitted > 0) {
        msg.append("\n")
        msg.append(Translator.raw("selection.feedback.points_omitted", omitted))
    }
}

private fun appendExistingScopePoints(
    msg: StringBuilder,
    scopeName: String,
    shapeType: GeoShapeType,
    oldPoints: List<BlockPos>
) {
    msg.append(Translator.raw("selection.feedback.modify.existing_header", scopeName, shapeType.name))
    if (oldPoints.isEmpty()) {
        msg.append("\n")
        msg.append(Translator.raw("selection.feedback.none"))
    } else {
        for ((idx, pt) in oldPoints.take(MAX_DISPLAYED_EXISTING_POINTS).withIndex()) {
            val role = getOldPointRole(shapeType, idx)
            msg.append("\n")
            msg.append(Translator.raw("selection.feedback.point_line_old", pt.x, pt.z, role))
        }
        val omitted = oldPoints.size - MAX_DISPLAYED_EXISTING_POINTS
        if (omitted > 0) {
            msg.append("\n")
            msg.append(Translator.raw("selection.feedback.modify.existing_omitted", omitted))
        }
    }
}

private fun getOldPointRole(shapeType: GeoShapeType, idx: Int): String = when (shapeType) {
    GeoShapeType.RECTANGLE -> when (idx) {
        0 -> Translator.raw("selection.feedback.modify.old.rect_corner1")
        else -> Translator.raw("selection.feedback.modify.old.rect_corner2")
    }
    GeoShapeType.CIRCLE -> when (idx) {
        0 -> Translator.raw("selection.feedback.modify.old.circle_center")
        else -> Translator.raw("selection.feedback.modify.old.circle_circumference")
    }
    GeoShapeType.POLYGON -> Translator.raw("selection.feedback.modify.old.polygon_vertex", idx + 1)
    else -> ""
}

private fun extractScopePoints(geometry: ShapeGeometry): List<BlockPos> = when (geometry) {
    is RectangleGeometry -> listOf(
        BlockPos(geometry.west, 0, geometry.north),
        BlockPos(geometry.east, 0, geometry.south)
    )
    is CircleGeometry -> listOf(
        BlockPos(geometry.centerX, 0, geometry.centerZ),
        BlockPos(geometry.centerX + geometry.radius, 0, geometry.centerZ)
    )
    is PolygonGeometry -> List(geometry.vertexCount) { index ->
        BlockPos(geometry.x(index), 0, geometry.z(index))
    }
    UnknownGeometry -> emptyList()
}

private fun getModifyNewPointRole(
    geometry: ShapeGeometry,
    newPoints: List<BlockPos>,
    idx: Int,
    oldPoints: List<BlockPos>
): String {
    return when (geometry) {
        is CircleGeometry -> {
            val cx = geometry.centerX
            val cz = geometry.centerZ
            val pt0IsCenter = newPoints.isNotEmpty() && newPoints[0].x == cx && newPoints[0].z == cz
            when {
                idx == 0 -> {
                    if (newPoints[0].x == cx && newPoints[0].z == cz)
                        Translator.raw("selection.feedback.modify.role.circle_1st_is_center")
                    else
                        Translator.raw("selection.feedback.modify.role.circle_1st")
                }
                idx == 1 -> {
                    if (pt0IsCenter)
                        Translator.raw("selection.feedback.modify.role.circle_2nd_new_center")
                    else
                        Translator.raw("selection.feedback.modify.role.circle_excess")
                }
                else -> Translator.raw("selection.feedback.modify.role.circle_excess")
            }
        }
        is RectangleGeometry -> Translator.raw("selection.feedback.modify.role.rect_anchor")
        is PolygonGeometry -> {
            val count = newPoints.size
            fun isExisting(pt: BlockPos) = oldPoints.any { it.x == pt.x && it.z == pt.z }
            when {
                count == 1 -> {
                    val pt = newPoints[0]
                    if (isExisting(pt))
                        Translator.raw("selection.feedback.modify.role.polygon_delete_or_move_src")
                    else
                        Translator.raw("selection.feedback.modify.role.polygon_insert_new")
                }
                count == 2 -> when (idx) {
                    0 -> {
                        if (isExisting(newPoints[0]))
                            Translator.raw("selection.feedback.modify.role.polygon_move_src")
                        else
                            Translator.raw("selection.feedback.modify.role.polygon_move_src_invalid")
                    }
                    else -> Translator.raw("selection.feedback.modify.role.polygon_move_dst")
                }
                else -> when {
                    idx == 0 -> {
                        if (isExisting(newPoints[0]))
                            Translator.raw("selection.feedback.modify.role.polygon_adj", 1)
                        else
                            Translator.raw("selection.feedback.modify.role.polygon_adj_invalid", 1)
                    }
                    idx == 1 -> {
                        if (isExisting(newPoints[1]))
                            Translator.raw("selection.feedback.modify.role.polygon_adj", 2)
                        else
                            Translator.raw("selection.feedback.modify.role.polygon_adj_invalid", 2)
                    }
                    idx == 2 -> Translator.raw("selection.feedback.modify.role.polygon_insert_pt")
                    else -> Translator.raw("selection.feedback.modify.role.polygon_excess")
                }
            }
        }
        UnknownGeometry -> ""
    }
}

private fun appendModifyGuidance(
    msg: StringBuilder,
    geometry: ShapeGeometry,
    newPoints: List<BlockPos>,
    scopeName: String,
    oldPoints: List<BlockPos>
) {
    val count = newPoints.size
    val guidanceRaw = when (geometry) {
        is CircleGeometry -> {
            val cx = geometry.centerX
            val cz = geometry.centerZ
            val existingRadius = geometry.radius
            when {
                count == 0 -> Translator.raw(
                    "selection.feedback.modify.guidance.circle.start",
                    scopeName, existingRadius
                )
                count == 1 -> {
                    val pt = newPoints[0]
                    if (pt.x == cx && pt.z == cz) {
                        Translator.raw(
                            "selection.feedback.modify.guidance.circle.1point_is_center",
                            cx, cz, existingRadius
                        )
                    } else {
                        val newRadius = hypot(pt.x.toDouble() - cx, pt.z.toDouble() - cz).toInt()
                        Translator.raw(
                            "selection.feedback.modify.guidance.circle.1point",
                            pt.x, pt.z, cx, cz, newRadius
                        )
                    }
                }
                count == 2 -> {
                    val pt0 = newPoints[0]; val pt1 = newPoints[1]
                    if (pt0.x == cx && pt0.z == cz) {
                        Translator.raw(
                            "selection.feedback.modify.guidance.circle.2points",
                            pt0.x, pt0.z, pt1.x, pt1.z, existingRadius
                        )
                    } else {
                        Translator.raw(
                            "selection.feedback.modify.guidance.circle.2points_invalid_pt1",
                            pt0.x, pt0.z, cx, cz
                        )
                    }
                }
                else -> Translator.raw("selection.feedback.modify.guidance.circle.excess")
            }
        }
        is RectangleGeometry -> {
            when {
                count == 0 -> Translator.raw(
                    "selection.feedback.modify.guidance.rect.start",
                    scopeName, geometry.west, geometry.north, geometry.east, geometry.south
                )
                count == 1 -> {
                    val pt = newPoints[0]
                    Translator.raw("selection.feedback.modify.guidance.rect.1point", pt.x, pt.z)
                }
                else -> Translator.raw("selection.feedback.modify.guidance.rect.excess")
            }
        }
        is PolygonGeometry -> {
            fun isExisting(pt: BlockPos) = oldPoints.any { it.x == pt.x && it.z == pt.z }
            fun areAdjacent(a: BlockPos, b: BlockPos): Boolean {
                val n = oldPoints.size
                val ia = oldPoints.indexOfFirst { it.x == a.x && it.z == a.z }
                val ib = oldPoints.indexOfFirst { it.x == b.x && it.z == b.z }
                return ia != -1 && ib != -1 && ((ia + 1) % n == ib || (ib + 1) % n == ia)
            }
            when {
                count == 0 -> Translator.raw(
                    "selection.feedback.modify.guidance.polygon.start",
                    scopeName, oldPoints.size
                )
                count == 1 -> {
                    val pt = newPoints[0]
                    if (isExisting(pt)) {
                        if (oldPoints.size <= 3)
                            Translator.raw("selection.feedback.modify.guidance.polygon.1point_delete_min", pt.x, pt.z)
                        else
                            Translator.raw("selection.feedback.modify.guidance.polygon.1point_delete", pt.x, pt.z, oldPoints.size)
                    } else {
                        val (adjA, adjB) = findNearestAdjacentPoints(oldPoints, pt)
                        Translator.raw(
                            "selection.feedback.modify.guidance.polygon.1point_insert",
                            pt.x, pt.z, adjA.x, adjA.z, adjB.x, adjB.z
                        )
                    }
                }
                count == 2 -> {
                    val src = newPoints[0]; val dst = newPoints[1]
                    when {
                        src.x == dst.x && src.z == dst.z ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.2points_same", src.x, src.z)
                        !isExisting(src) ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.2points_invalid_pt1", src.x, src.z)
                        else ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.2points", src.x, src.z, dst.x, dst.z)
                    }
                }
                count == 3 -> {
                    val adj1 = newPoints[0]; val adj2 = newPoints[1]; val ins = newPoints[2]
                    when {
                        !isExisting(adj1) ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.3pts_adj1_not_found", adj1.x, adj1.z)
                        !isExisting(adj2) ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.3pts_adj2_not_found", adj2.x, adj2.z)
                        adj1.x == adj2.x && adj1.z == adj2.z ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.3pts_adj_same")
                        !areAdjacent(adj1, adj2) ->
                            Translator.raw("selection.feedback.modify.guidance.polygon.3pts_not_adjacent", adj1.x, adj1.z, adj2.x, adj2.z)
                        else -> {
                            val newPoly = evaluateModifyPolygonExplicitInsert(adj1, adj2, ins, geometry)
                            if (newPoly != null)
                                Translator.raw(
                                    "selection.feedback.modify.guidance.polygon.3plus",
                                    adj1.x, adj1.z, adj2.x, adj2.z, ins.x, ins.z
                                )
                            else
                                Translator.raw(
                                    "selection.feedback.modify.guidance.polygon.3plus_invalid",
                                    adj1.x, adj1.z, adj2.x, adj2.z, ins.x, ins.z
                                )
                        }
                    }
                }
                else -> Translator.raw("selection.feedback.modify.guidance.polygon.excess")
            }
        }
        UnknownGeometry -> ""
    }
    if (guidanceRaw.isNotEmpty()) msg.append(guidanceRaw)

    val warningRaw = buildModifyValidationWarning(geometry, newPoints)
    if (warningRaw.isNotEmpty()) {
        msg.append("\n")
        msg.append(warningRaw)
    }
}

private fun buildModifyValidationWarning(geometry: ShapeGeometry, newPoints: List<BlockPos>): String {
    return when (geometry) {
        is CircleGeometry -> {
            if (newPoints.size < 1) return ""
            val cx = geometry.centerX
            val cz = geometry.centerZ
            if (newPoints.size == 1) {
                val pt = newPoints[0]
                if (pt.x == cx && pt.z == cz) return ""
                val newRadius = hypot(pt.x.toDouble() - cx, pt.z.toDouble() - cz)
                when {
                    newRadius < GeoConfig.MIN_CIRCLE_RADIUS.value ->
                        Translator.raw(
                            "selection.feedback.warn.circle.too_small",
                            String.format(java.util.Locale.ROOT, "%.1f", newRadius),
                            GeoConfig.MIN_CIRCLE_RADIUS.value.toInt()
                        )
                    else -> ""
                }
            } else ""
        }
        is PolygonGeometry -> ""
        else -> ""
    }
}

private fun buildNormalParticleNote(shape: GeoShapeType, isAuto: Boolean, points: List<BlockPos>): String {
    val count = points.size
    val effectiveShape = if (isAuto && count > 2) GeoShapeType.POLYGON else shape
    return when {
        count < 2 -> Translator.raw("selection.feedback.particles.pillar_only")
        effectiveShape == GeoShapeType.CIRCLE && count >= 2 ->
            Translator.raw("selection.feedback.particles.circle_complete")
        effectiveShape == GeoShapeType.RECTANGLE && count >= 2 ->
            Translator.raw("selection.feedback.particles.rect_complete")
        effectiveShape == GeoShapeType.POLYGON && count >= 3 -> {
            val isValid = isPolygonVertexCountSupported(count) &&
                points.distinct().size == points.size &&
                isConvex(points) &&
                checkPolygonSize(points) == null
            if (isValid) Translator.raw("selection.feedback.particles.polygon_valid")
            else Translator.raw("selection.feedback.particles.polygon_invalid")
        }
        else -> Translator.raw("selection.feedback.particles.pillar_only")
    }
}

private fun appendShapeParameterBlock(msg: StringBuilder, effectiveShape: GeoShapeType, isAuto: Boolean) {
    if (isAuto) {
        msg.append(Translator.raw("selection.feedback.shape.header.auto", effectiveShape.name))
    } else {
        msg.append(Translator.raw("selection.feedback.shape.header", effectiveShape.name))
    }
    val pointsKey = when (effectiveShape) {
        GeoShapeType.CIRCLE -> "selection.feedback.shape.points.circle"
        GeoShapeType.RECTANGLE -> "selection.feedback.shape.points.rect"
        GeoShapeType.POLYGON -> "selection.feedback.shape.points.polygon"
        else -> null
    }
    if (pointsKey != null) {
        msg.append("\n")
        msg.append(Translator.raw(pointsKey))
    }
}

private fun appendModifyShapeParameterBlock(msg: StringBuilder, shapeType: GeoShapeType, scope: GeoScope) {
    msg.append(Translator.raw("selection.feedback.modify.shape.header", scope.scopeName, shapeType.name))
    val pointsKey = when (shapeType) {
        GeoShapeType.CIRCLE -> "selection.feedback.modify.shape.points.circle"
        GeoShapeType.RECTANGLE -> "selection.feedback.modify.shape.points.rect"
        GeoShapeType.POLYGON -> "selection.feedback.modify.shape.points.polygon"
        else -> null
    }
    if (pointsKey != null) {
        msg.append("\n")
        msg.append(Translator.raw(pointsKey))
    }
}
