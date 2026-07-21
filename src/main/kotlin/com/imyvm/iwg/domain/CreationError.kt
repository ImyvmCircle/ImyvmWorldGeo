package com.imyvm.iwg.domain

import com.imyvm.iwg.util.geo.IntersectionDetail

sealed class CreationError {
    data object DuplicatedPoints : CreationError()
    data object InsufficientPoints : CreationError()
    data object CoincidentPoints : CreationError()
    data object UnderSizeLimit : CreationError()
    data object CoordinateRangeExceeded : CreationError()
    data object PolygonVertexLimitExceeded : CreationError()
    data object UnderBoundingBoxLimit : CreationError()
    data object AspectRatioInvalid : CreationError()
    data object EdgeTooShort : CreationError()
    data object NotConvex : CreationError()
    data class SubSpaceOutsideParentScope(val regionName: String, val scopeName: String) : CreationError()
    data class IntersectionBetweenScopes(val details: List<IntersectionDetail>) : CreationError()
}
