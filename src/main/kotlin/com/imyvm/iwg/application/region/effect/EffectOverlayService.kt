package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.immutableSnapshot
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.LazyTicker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectOverlayService {

    private val overlaysByScope: ConcurrentHashMap<AssignedScopeId, MutableList<TimedEffectOverlay>> = ConcurrentHashMap()
    // ponytail: one lock is enough for low-frequency deletion/apply; use per-scope stripes only if contention is measured.
    private val scopeLifecycleMonitor = Any()

    fun register() {
        LazyTicker.registerTask { _ ->
            sweepExpired(System.currentTimeMillis())
        }
    }

    fun applyTimedEffectOverlay(overlay: TimedEffectOverlay): String =
        applyTimedEffectOverlay(overlay) { RegionDatabase.getScopeByAssignedId(it) != null }

    internal fun applyTimedEffectOverlay(
        overlay: TimedEffectOverlay,
        scopeExists: (AssignedScopeId) -> Boolean
    ): String = withScopeLifecycle {
        require(scopeExists(overlay.scopeId)) { "scope does not exist" }
        val storedOverlay = overlay.immutableSnapshot()
        overlaysByScope.compute(storedOverlay.scopeId) { _, existing ->
            val list = existing ?: mutableListOf()
            synchronized(list) {
                list.removeAll { it.overlayId == storedOverlay.overlayId }
                list.add(storedOverlay)
            }
            list
        }
        storedOverlay.overlayId
    }

    internal fun <T> withScopeLifecycle(operation: () -> T): T =
        synchronized(scopeLifecycleMonitor, operation)

    fun clearTimedEffectOverlay(scopeId: ScopeId, overlayId: String): Boolean {
        val assignedScopeId = AssignedScopeId.from(scopeId) ?: return false
        return clearTimedEffectOverlay(assignedScopeId, overlayId)
    }

    fun clearTimedEffectOverlay(scopeId: AssignedScopeId, overlayId: String): Boolean {
        var removed = false
        overlaysByScope.computeIfPresent(scopeId) { _, list ->
            synchronized(list) {
                removed = list.removeAll { it.overlayId == overlayId }
                list.takeIf { it.isNotEmpty() }
            }
        }
        return removed
    }

    fun queryOverlay(scopeId: ScopeId, nowMillis: Long = System.currentTimeMillis()): Map<EffectKey, Int> {
        val assignedScopeId = AssignedScopeId.from(scopeId) ?: return emptyMap()
        return queryOverlay(assignedScopeId, nowMillis)
    }

    fun queryOverlay(scopeId: AssignedScopeId, nowMillis: Long = System.currentTimeMillis()): Map<EffectKey, Int> {
        val list = overlaysByScope[scopeId] ?: return emptyMap()
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
        val assignedScopeId = AssignedScopeId.from(scopeId) ?: return emptyList()
        return queryActiveOverlays(assignedScopeId, nowMillis)
    }

    fun queryActiveOverlays(scopeId: AssignedScopeId, nowMillis: Long = System.currentTimeMillis()): List<TimedEffectOverlay> {
        val list = overlaysByScope[scopeId] ?: return emptyList()
        val snapshot = synchronized(list) { list.toList() }
        return snapshot.filter { it.startTickMillis <= nowMillis && nowMillis < it.endTickMillis }
    }

    @Suppress("UNUSED_PARAMETER")
    fun queryOverlayForPlayer(scopeId: ScopeId, playerUUID: UUID, nowMillis: Long = System.currentTimeMillis()): Map<EffectKey, Int> {
        // Overlay is scope-global; per-player overlays would be added here when needed.
        return queryOverlay(scopeId, nowMillis)
    }

    fun clearScope(scopeId: AssignedScopeId) {
        withScopeLifecycle { overlaysByScope.remove(scopeId) }
    }

    internal fun sweepExpired(nowMillis: Long) {
        for (scopeId in overlaysByScope.keys) {
            overlaysByScope.computeIfPresent(scopeId) { _, list ->
                synchronized(list) {
                    list.removeAll { it.endTickMillis <= nowMillis }
                    list.takeIf { it.isNotEmpty() }
                }
            }
        }
    }
}
