package com.imyvm.iwg.util.ui

import com.imyvm.iwg.domain.Region
import net.minecraft.text.Text

sealed class CreationError {
    data object DuplicatedPoints : CreationError()
    data object InsufficientPoints : CreationError()
    data object CoincidentPoints : CreationError()
    data object UnderSizeLimit : CreationError()
    data object UnderBoundingBoxLimit : CreationError()
    data object AspectRatioInvalid : CreationError()
    data object EdgeTooShort : CreationError()
    data object NotConvex : CreationError()
    data object IntersectionBetweenScopes : CreationError()
}

fun errorMessage(
    error: CreationError,
    shapeType: Region.Companion.GeoShapeType
): Text? = when (error) {
    CreationError.DuplicatedPoints -> Translator.tr("error.duplicated_points")
    CreationError.InsufficientPoints -> Translator.tr("error.insufficient_points", shapeType.name.lowercase())
    CreationError.CoincidentPoints -> Translator.tr("error.coincident_points")
    CreationError.UnderSizeLimit -> when (shapeType) {
        Region.Companion.GeoShapeType.RECTANGLE -> Translator.tr("error.rectangle_too_small")
        Region.Companion.GeoShapeType.CIRCLE -> Translator.tr("error.circle_too_small")
        Region.Companion.GeoShapeType.POLYGON -> Translator.tr("error.polygon_too_small")
        else -> Translator.tr("error.generic_too_small")
    }
    CreationError.UnderBoundingBoxLimit -> Translator.tr("error.under_bounding_box_limit")
    CreationError.AspectRatioInvalid -> Translator.tr("error.aspect_ratio_invalid")
    CreationError.EdgeTooShort -> Translator.tr("error.edge_too_short")
    CreationError.NotConvex -> Translator.tr("error.not_convex")
    CreationError.IntersectionBetweenScopes -> Translator.tr("error.intersection_between_scopes")
}