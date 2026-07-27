@file:JvmName("RuleHelper")

package com.imyvm.iwg.application.region.rule.helper

import com.imyvm.iwg.application.region.setting.defaultRuleValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

fun getRegionRuleValue(region: Region, key: RuleKey): Boolean? =
    region.settingStore.rule(key)

fun getScopeRuleValue(region: Region, scope: GeoScope, key: RuleKey): Boolean? {
    require(region.containsScope(scope)) { "scope does not belong to region" }
    return scope.settingStore.rule(key)
        ?: getRegionRuleValue(region, key)
}

fun getScopeRuleValue(region: Region, key: RuleKey, scope: GeoScope): Boolean? =
    getScopeRuleValue(region, scope, key)

fun getEffectiveRegionRuleValue(region: Region, key: RuleKey): Boolean =
    getRegionRuleValue(region, key) ?: defaultRuleValue(key)

fun getEffectiveScopeRuleValue(region: Region, scope: GeoScope, key: RuleKey): Boolean =
    getScopeRuleValue(region, scope, key) ?: defaultRuleValue(key)

fun getEffectiveRuleValueAt(world: Level, pos: BlockPos, key: RuleKey): Boolean? {
    val (region, scope) = RegionDatabase.getRegionAndScopeAt(world, pos.x, pos.z) ?: return null
    return getEffectiveScopeRuleValue(region, scope, key)
}

fun getRegionRuleValue(region: Region, key: ExtensionRuleKey): Boolean? {
    require(ExtensionSettingRegistry.isRegisteredRuleKey(key.id)) { "Extension rule key '${key.id}' is not registered." }
    return region.settingStore.rule(key)
}

fun getScopeRuleValue(region: Region, scope: GeoScope, key: ExtensionRuleKey): Boolean? {
    require(ExtensionSettingRegistry.isRegisteredRuleKey(key.id)) { "Extension rule key '${key.id}' is not registered." }
    require(region.containsScope(scope)) { "scope does not belong to region" }
    return scope.settingStore.rule(key)
        ?: getRegionRuleValue(region, key)
}

fun getScopeRuleValue(region: Region, key: ExtensionRuleKey, scope: GeoScope): Boolean? =
    getScopeRuleValue(region, scope, key)

internal fun getEffectiveRegionRuleValue(region: Region, key: ExtensionRuleKey): Boolean =
    getRegionRuleValue(region, key) ?: defaultRuleValue(key)

internal fun getEffectiveScopeRuleValue(region: Region, scope: GeoScope, key: ExtensionRuleKey): Boolean =
    getScopeRuleValue(region, scope, key) ?: defaultRuleValue(key)

@Deprecated("Use getRegionRuleValue or getScopeRuleValue")
fun getRuleValue(region: Region, key: RuleKey, scope: GeoScope? = null): Boolean? =
    if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)

@Deprecated("Use an explicit default, region, or scope rule query")
fun getEffectiveRuleValue(region: Region?, key: RuleKey, scope: GeoScope? = null): Boolean {
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return defaultRuleValue(key)
    }
    return if (scope == null) getEffectiveRegionRuleValue(region, key) else getEffectiveScopeRuleValue(region, scope, key)
}

@Deprecated("Use getRegionRuleValue or getScopeRuleValue")
fun getRuleValue(region: Region, key: ExtensionRuleKey, scope: GeoScope? = null): Boolean? =
    if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)
