package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.GeoShapeType
import net.minecraft.text.Text

fun errorMessage(
    error: CreationError,
    shapeType: GeoShapeType
): Text? = when (error) {
    CreationError.DuplicatedPoints -> Translator.tr("error.duplicated_points")
    CreationError.InsufficientPoints -> Translator.tr("error.insufficient_points", shapeType.name.lowercase())
    CreationError.CoincidentPoints -> Translator.tr("error.coincident_points")
    CreationError.UnderSizeLimit -> when (shapeType) {
        GeoShapeType.RECTANGLE -> Translator.tr("error.rectangle_too_small")
        GeoShapeType.CIRCLE -> Translator.tr("error.circle_too_small")
        GeoShapeType.POLYGON -> Translator.tr("error.polygon_too_small")
        else -> Translator.tr("error.generic_too_small")
    }
    CreationError.UnderBoundingBoxLimit -> Translator.tr("error.under_bounding_box_limit")
    CreationError.AspectRatioInvalid -> Translator.tr("error.aspect_ratio_invalid")
    CreationError.EdgeTooShort -> Translator.tr("error.edge_too_short")
    CreationError.NotConvex -> Translator.tr("error.not_convex")
    CreationError.IntersectionBetweenScopes -> Translator.tr("error.intersection_between_scopes")
}