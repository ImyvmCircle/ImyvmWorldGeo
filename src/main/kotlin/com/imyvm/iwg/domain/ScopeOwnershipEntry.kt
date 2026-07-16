package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.AssignedScopeId

data class ScopeOwnershipEntry(
    val scopeIdRaw: Long,
    val fromRegionNumberId: Int,
    val toRegionNumberId: Int,
    val changedAtMillis: Long
) {
    val scopeId: AssignedScopeId =
        AssignedScopeId.fromRaw(scopeIdRaw) ?: throw IllegalArgumentException("scope id is not assigned")

    init {
        require(fromRegionNumberId > 0) { "source region id must be positive" }
        require(toRegionNumberId > 0) { "target region id must be positive" }
        require(fromRegionNumberId != toRegionNumberId) { "ownership transfer must change region" }
        require(changedAtMillis >= 0L) { "ownership change time must not be negative" }
    }
}
