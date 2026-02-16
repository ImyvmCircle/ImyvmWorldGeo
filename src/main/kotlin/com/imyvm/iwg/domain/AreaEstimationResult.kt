package com.imyvm.iwg.domain

sealed class AreaEstimationResult {
    data class Success(val area: Double) : AreaEstimationResult()
    data class Error(val error: CreationError) : AreaEstimationResult()
}
