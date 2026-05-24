package com.imyvm.iwg.domain

data class ScopeOwnershipEntry(
    val scopeIdRaw: Long,
    val fromRegionNumberId: Int,
    val toRegionNumberId: Int,
    val changedAtMillis: Long
)
