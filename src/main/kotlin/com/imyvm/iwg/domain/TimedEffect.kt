package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.AssignedScopeId
import java.util.Collections

data class TimedEffect(
    val effectKey: EffectKey,
    val amplifier: Int
)

/** A validated scope-global effect overlay. Active services retain an immutable effect snapshot. */
data class TimedEffectOverlay(
    val overlayId: String,
    val scopeIdRaw: Long,
    val effects: List<TimedEffect>,
    val startTickMillis: Long,
    val endTickMillis: Long,
    val priority: Int,
    val source: String
) {
    val scopeId: AssignedScopeId =
        AssignedScopeId.fromRaw(scopeIdRaw) ?: throw IllegalArgumentException("scope id is not assigned")

    init {
        require(overlayId.isNotBlank()) { "overlay id must not be blank" }
        require(source.isNotBlank()) { "overlay source must not be blank" }
        require(startTickMillis < endTickMillis) { "overlay start must be before end" }
        require(effects.isNotEmpty()) { "overlay must contain at least one effect" }
        require(effects.all { it.amplifier in 0..255 }) { "effect amplifier must be between 0 and 255" }
        require(effects.distinctBy(TimedEffect::effectKey).size == effects.size) {
            "overlay must not contain duplicate effect keys"
        }
    }
}

internal fun TimedEffectOverlay.immutableSnapshot(): TimedEffectOverlay =
    copy(effects = Collections.unmodifiableList(ArrayList(effects)))
