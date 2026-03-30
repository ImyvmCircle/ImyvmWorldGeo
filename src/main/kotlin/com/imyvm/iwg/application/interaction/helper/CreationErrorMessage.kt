package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.util.geo.IntersectionDetail
import com.imyvm.iwg.util.geo.VertexInsideInfo
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.network.chat.Component

fun errorMessage(
    error: CreationError,
    shapeType: GeoShapeType
): List<Component> = when (error) {
    CreationError.DuplicatedPoints -> listOfNotNull(Translator.tr("error.duplicated_points")!!)
    CreationError.InsufficientPoints -> listOfNotNull(Translator.tr("error.insufficient_points", shapeType.name.lowercase())!!)
    CreationError.CoincidentPoints -> listOfNotNull(Translator.tr("error.coincident_points")!!)
    CreationError.UnderSizeLimit -> listOfNotNull(when (shapeType) {
        GeoShapeType.RECTANGLE -> Translator.tr("error.rectangle_too_small")!!
        GeoShapeType.CIRCLE -> Translator.tr("error.circle_too_small")!!
        GeoShapeType.POLYGON -> Translator.tr("error.polygon_too_small")!!
        else -> Translator.tr("error.generic_too_small")!!
    })
    CreationError.UnderBoundingBoxLimit -> listOfNotNull(Translator.tr("error.under_bounding_box_limit")!!)
    CreationError.AspectRatioInvalid -> listOfNotNull(Translator.tr("error.aspect_ratio_invalid")!!)
    CreationError.EdgeTooShort -> listOfNotNull(Translator.tr("error.edge_too_short")!!)
    CreationError.NotConvex -> listOfNotNull(Translator.tr("error.not_convex")!!)
    is CreationError.IntersectionBetweenScopes -> buildIntersectionErrorMessages(error.details, shapeType)
}

private fun buildIntersectionErrorMessages(details: List<IntersectionDetail>, shapeType: GeoShapeType): List<Component> {
    val lines = mutableListOf<Component>()
    Translator.tr("error.intersection_between_scopes.header")!!?.let { lines.add(it) }
    for (detail in details) {
        val shapeDesc = getShapeParamDescription(detail.shape)
        Translator.tr("error.intersection_between_scopes.scope_line", detail.regionName, detail.scopeName, shapeDesc)!!?.let { lines.add(it) }
        if (detail.verticesInside.isNotEmpty() && shapeType in listOf(GeoShapeType.POLYGON, GeoShapeType.RECTANGLE)) {
            val vertexStr = formatVertexList(detail.verticesInside, shapeType)
            Translator.tr("error.intersection_between_scopes.vertices_inside", vertexStr)!!?.let { lines.add(it) }
        }
    }
    return lines
}

private fun getShapeParamDescription(shape: com.imyvm.iwg.domain.component.GeoShape): String {
    return when (shape.geoShapeType) {
        GeoShapeType.RECTANGLE -> Translator.raw(
            "error.intersection.shape.rectangle",
            shape.shapeParameter[0], shape.shapeParameter[1],
            shape.shapeParameter[2], shape.shapeParameter[3]
        ) ?: ""
        GeoShapeType.CIRCLE -> Translator.raw(
            "error.intersection.shape.circle",
            shape.shapeParameter[0], shape.shapeParameter[1], shape.shapeParameter[2]
        ) ?: ""
        GeoShapeType.POLYGON -> {
            val coords = shape.shapeParameter.chunked(2).joinToString(" ") { "(${it[0]},${it[1]})" }
            Translator.raw("error.intersection.shape.polygon", coords) ?: ""
        }
        else -> ""
    }
}

private fun formatVertexList(vertices: List<VertexInsideInfo>, shapeType: GeoShapeType): String {
    return vertices.joinToString(", ") { info ->
        when (shapeType) {
            GeoShapeType.RECTANGLE -> {
                val cornerKey = when (info.index) {
                    1 -> "error.intersection.vertex.rect_nw"
                    2 -> "error.intersection.vertex.rect_ne"
                    3 -> "error.intersection.vertex.rect_se"
                    4 -> "error.intersection.vertex.rect_sw"
                    else -> null
                }
                if (cornerKey != null) Translator.raw(cornerKey, info.x, info.z) ?: "(${info.x},${info.z})"
                else Translator.raw("error.intersection.vertex.polygon", info.index, info.x, info.z) ?: "#${info.index}(${info.x},${info.z})"
            }
            else -> Translator.raw("error.intersection.vertex.polygon", info.index, info.x, info.z) ?: "#${info.index}(${info.x},${info.z})"
        }
    }
}