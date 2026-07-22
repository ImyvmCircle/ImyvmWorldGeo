package com.imyvm.iwg.domain

enum class NaturalPeriodKind {
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR
}

data class NaturalPeriodTransition(
    val kind: NaturalPeriodKind,
    val previousId: String,
    val currentId: String,
    val unixMillis: Long
)
