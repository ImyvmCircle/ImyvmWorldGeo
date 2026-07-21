package com.imyvm.iwg.domain

enum class NaturalPeriodKind {
    HOUR,
    DAY,
    WEEK,
    MONTH
}

data class NaturalPeriodTransition(
    val kind: NaturalPeriodKind,
    val previousId: String,
    val currentId: String,
    val unixMillis: Long
)
