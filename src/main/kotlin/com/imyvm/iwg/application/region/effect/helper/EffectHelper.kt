package com.imyvm.iwg.application.region.effect.helper

import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.*
import java.util.UUID

fun getRegionEffectValue(region: Region, playerUUID: UUID, key: EffectKey): Int? {
    return region.settingStore.playerEffect(key, playerUUID)
        ?: region.settingStore.globalEffect(key)
}

fun getScopeEffectValue(region: Region, scope: GeoScope, playerUUID: UUID, key: EffectKey): Int? {
    require(region.containsScope(scope)) { "scope does not belong to region" }
    return resolveScopeEffectValue(region, scope, playerUUID, key, scopeOverlay(scope))
}

fun getRegionActiveEffects(region: Region, playerUUID: UUID): Map<EffectKey, Int> =
    region.settingStore.effectKeys().mapNotNull { key ->
        getRegionEffectValue(region, playerUUID, key)?.let { key to it }
    }.toMap()

fun getScopeActiveEffects(region: Region, scope: GeoScope, playerUUID: UUID): Map<EffectKey, Int> {
    require(region.containsScope(scope)) { "scope does not belong to region" }
    val overlay = scopeOverlay(scope)
    val keys = buildSet {
        addAll(scope.settingStore.effectKeys())
        addAll(region.settingStore.effectKeys())
        addAll(overlay.keys)
    }
    return keys.mapNotNull { key ->
        resolveScopeEffectValue(region, scope, playerUUID, key, overlay)?.let { key to it }
    }.toMap()
}

@Deprecated("Use getRegionEffectValue or getScopeEffectValue")
fun getEffectValue(region: Region?, playerUUID: UUID, key: EffectKey, scope: GeoScope? = null): Int? {
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return null
    }
    return if (scope == null) getRegionEffectValue(region, playerUUID, key)
    else getScopeEffectValue(region, scope, playerUUID, key)
}

@Deprecated("Use getRegionActiveEffects or getScopeActiveEffects")
fun getActiveEffects(region: Region, playerUUID: UUID, scope: GeoScope? = null): Map<EffectKey, Int> =
    if (scope == null) getRegionActiveEffects(region, playerUUID)
    else getScopeActiveEffects(region, scope, playerUUID)

private fun resolveScopeEffectValue(
    region: Region,
    scope: GeoScope,
    playerUUID: UUID,
    key: EffectKey,
    overlay: Map<EffectKey, Int>
): Int? {
    return scope.settingStore.playerEffect(key, playerUUID)
        ?: region.settingStore.playerEffect(key, playerUUID)
        ?: overlay[key]
        ?: scope.settingStore.globalEffect(key)
        ?: region.settingStore.globalEffect(key)
}

private fun scopeOverlay(scope: GeoScope): Map<EffectKey, Int> =
    scope.assignedScopeIdOrNull?.let(EffectOverlayService::queryOverlay) ?: emptyMap()
