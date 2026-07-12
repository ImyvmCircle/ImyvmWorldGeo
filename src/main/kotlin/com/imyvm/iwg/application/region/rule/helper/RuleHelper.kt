@file:JvmName("RuleHelper")

package com.imyvm.iwg.application.region.rule.helper

import com.imyvm.iwg.application.interaction.getDefaultValueForRule
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.*

fun getRegionRuleValue(region: Region, key: RuleKey): Boolean? =
    region.settings.filterIsInstance<RuleSetting>().firstOrNull { it.key == key }?.value

fun getScopeRuleValue(region: Region, scope: GeoScope, key: RuleKey): Boolean? {
    require(region.geometryScope.contains(scope)) { "scope does not belong to region" }
    return scope.settings.filterIsInstance<RuleSetting>().firstOrNull { it.key == key }?.value
        ?: getRegionRuleValue(region, key)
}

fun getScopeRuleValue(region: Region, key: RuleKey, scope: GeoScope): Boolean? =
    getScopeRuleValue(region, scope, key)

fun getEffectiveRegionRuleValue(region: Region, key: RuleKey): Boolean =
    getRegionRuleValue(region, key) ?: getDefaultValueForRule(key)

fun getEffectiveScopeRuleValue(region: Region, scope: GeoScope, key: RuleKey): Boolean =
    getScopeRuleValue(region, scope, key) ?: getDefaultValueForRule(key)

fun getRegionRuleValue(region: Region, key: ExtensionRuleKey): Boolean? =
    region.settings.filterIsInstance<ExtensionRuleSetting>().firstOrNull { it.key == key }?.value

fun getScopeRuleValue(region: Region, scope: GeoScope, key: ExtensionRuleKey): Boolean? {
    require(region.geometryScope.contains(scope)) { "scope does not belong to region" }
    return scope.settings.filterIsInstance<ExtensionRuleSetting>().firstOrNull { it.key == key }?.value
        ?: getRegionRuleValue(region, key)
}

fun getScopeRuleValue(region: Region, key: ExtensionRuleKey, scope: GeoScope): Boolean? =
    getScopeRuleValue(region, scope, key)

@Deprecated("Use getRegionRuleValue or getScopeRuleValue")
fun getRuleValue(region: Region, key: RuleKey, scope: GeoScope? = null): Boolean? =
    if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)

@Deprecated("Use an explicit default, region, or scope rule query")
fun getEffectiveRuleValue(region: Region?, key: RuleKey, scope: GeoScope? = null): Boolean {
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return getDefaultValueForRule(key)
    }
    return if (scope == null) getEffectiveRegionRuleValue(region, key) else getEffectiveScopeRuleValue(region, scope, key)
}

@Deprecated("Use getRegionRuleValue or getScopeRuleValue")
fun getRuleValue(region: Region, key: ExtensionRuleKey, scope: GeoScope? = null): Boolean? =
    if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)
