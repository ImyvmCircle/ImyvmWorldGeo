package com.imyvm.iwg.application.region.effect.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.GeoScope
import java.util.UUID

fun getEffectValue(region: Region?, playerUUID: UUID, key: EffectKey, scope: GeoScope? = null): Int? {
    if (region == null) return null
    return resolveEffectValue(region, playerUUID, key, scope)
}

fun getActiveEffects(region: Region, playerUUID: UUID, scope: GeoScope? = null): Map<EffectKey, Int> {
    val allKeys = mutableSetOf<EffectKey>()
    scope?.settings?.filterIsInstance<EffectSetting>()?.forEach { allKeys.add(it.key) }
    region.settings.filterIsInstance<EffectSetting>().forEach { allKeys.add(it.key) }

    val result = mutableMapOf<EffectKey, Int>()
    for (key in allKeys) {
        val value = resolveEffectValue(region, playerUUID, key, scope)
        if (value != null) result[key] = value
    }
    return result
}

private fun resolveEffectValue(region: Region, playerUUID: UUID, key: EffectKey, scope: GeoScope?): Int? {
    scope?.settings?.filterIsInstance<EffectSetting>()?.let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let { return it.value }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let { return it.value }
    }
    region.settings.filterIsInstance<EffectSetting>().let { settings ->
        settings.firstOrNull { it.isPersonal && it.key == key && it.playerUUID == playerUUID }?.let { return it.value }
        settings.firstOrNull { !it.isPersonal && it.key == key }?.let { return it.value }
    }
    return null
}