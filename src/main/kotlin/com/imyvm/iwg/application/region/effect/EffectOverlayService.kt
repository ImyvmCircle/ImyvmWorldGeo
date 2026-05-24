package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.infra.LazyTicker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectOverlayService {

    private val overlaysByScope: ConcurrentHashMap<Long, MutableList<TimedEffectOverlay>> = ConcurrentHashMap()

    fun register() {
        LazyTicker.registerTask { _ ->
            sweepExpired(System.currentTimeMillis())
        }
    }

    fun applyTimedEffectOverlay(overlay: TimedEffectOverlay): String {
        val list = overlaysByScope.getOrPut(overlay.scopeIdRaw) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it.overlayId == overlay.overlayId }
            list.add(overlay)
        }
        return overlay.overlayId
    }

    fun clearTimedEffectOverlay(scopeId: ScopeId, overlayId: String): Boolean {
        val list = overlaysByScope[scopeId.raw] ?: return false
        return synchronized(list) {
            val removed = list.removeAll { it.overlayId == overlayId }
            if (list.isEmpty()) overlaysByScope.remove(scopeId.raw)
            removed
        }
    }

    fun queryOverlay(scopeId: ScopeId, nowMillis: Long = System.currentTimeMillis()): Map<EffectKey, Int> {
        val list = overlaysByScope[scopeId.raw] ?: return emptyMap()
        val snapshot = synchronized(list) { list.toList() }
        val active = snapshot.filter { it.startTickMillis <= nowMillis && nowMillis < it.endTickMillis }
        if (active.isEmpty()) return emptyMap()
        val byPriority = active.sortedByDescending { it.priority }
        val result = mutableMapOf<EffectKey, Int>()
        for (overlay in byPriority) {
            for (effect in overlay.effects) {
                result.putIfAbsent(effect.effectKey, effect.amplifier)
            }
        }
        return result
    }

    fun queryActiveOverlays(scopeId: ScopeId, nowMillis: Long = System.currentTimeMillis()): List<TimedEffectOverlay> {
        val list = overlaysByScope[scopeId.raw] ?: return emptyList()
        val snapshot = synchronized(list) { list.toList() }
        return snapshot.filter { it.startTickMillis <= nowMillis && nowMillis < it.endTickMillis }
    }

    @Suppress("UNUSED_PARAMETER")
    fun queryOverlayForPlayer(scopeId: ScopeId, playerUUID: UUID, nowMillis: Long = System.currentTimeMillis()): Map<EffectKey, Int> {
        // Overlay is scope-global; per-player overlays would be added here when needed.
        return queryOverlay(scopeId, nowMillis)
    }

    private fun sweepExpired(nowMillis: Long) {
        val iterator = overlaysByScope.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val list = entry.value
            synchronized(list) {
                list.removeAll { it.endTickMillis <= nowMillis }
                if (list.isEmpty()) iterator.remove()
            }
        }
    }
}
