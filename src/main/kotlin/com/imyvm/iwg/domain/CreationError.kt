package com.imyvm.iwg.domain

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