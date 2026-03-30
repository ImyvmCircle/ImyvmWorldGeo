package com.imyvm.iwg.application.selection

import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState

fun SelectionState.getEffectiveShapeType(): GeoShapeType {
    return when (val shape = hypotheticalShape) {
        is HypotheticalShape.Normal -> shape.shapeType
        is HypotheticalShape.ModifyExisting -> shape.scope.geoShape?.geoShapeType ?: GeoShapeType.RECTANGLE
        null -> if (points.size <= 2) GeoShapeType.RECTANGLE else GeoShapeType.POLYGON
    }
}