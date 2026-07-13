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
}
