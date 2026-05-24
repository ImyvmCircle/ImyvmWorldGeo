package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.EffectKey

data class TimedEffect(
    val effectKey: EffectKey,
    val amplifier: Int
)

data class TimedEffectOverlay(
    val overlayId: String,
    val scopeIdRaw: Long,
    val effects: List<TimedEffect>,
    val startTickMillis: Long,
    val endTickMillis: Long,
    val priority: Int,
    val source: String
)
