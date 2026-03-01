@file:JvmName("RuleHelper")

package com.imyvm.iwg.application.region.rule.helper

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting

fun getRuleValue(region: Region, key: RuleKey, scope: GeoScope? = null): Boolean? {
    scope?.settings?.filterIsInstance<RuleSetting>()?.firstOrNull { it.key == key }?.let { return it.value }
    region.settings.filterIsInstance<RuleSetting>().firstOrNull { it.key == key }?.let { return it.value }
    return null
}
