package com.imyvm.iwg.application.region.rule.helper

import com.imyvm.iwg.application.region.setting.defaultRuleValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.ExtensionRuleSetting
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuleHelperTest {
    @Test
    fun `effective scope rules apply default region and scope precedence`() {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            settings = mutableListOf(
                RuleSetting(RuleKey.SPAWN_MONSTERS, true),
                RuleSetting(RuleKey.TNT_BLOCK_PROTECTION, true)
            ),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        )
        val region = Region(
            "region",
            7,
            mutableListOf(scope),
            settings = mutableListOf(
                RuleSetting(RuleKey.SPAWN_MONSTERS, false),
                RuleSetting(RuleKey.PISTON, false)
            )
        )

        assertEquals(
            defaultRuleValue(RuleKey.DISPENSER),
            getEffectiveScopeRuleValue(region, scope, RuleKey.DISPENSER)
        )
        assertFalse(getEffectiveScopeRuleValue(region, scope, RuleKey.PISTON))
        assertTrue(getEffectiveScopeRuleValue(region, scope, RuleKey.SPAWN_MONSTERS))
        assertTrue(getEffectiveScopeRuleValue(region, scope, RuleKey.TNT_BLOCK_PROTECTION))
    }

    @Test
    fun `extension effective rules use registered default and scope precedence`() {
        ExtensionSettingRegistry.registerRuleKey("test:effective_rule", false)
        val key = ExtensionSettingRegistry.ruleKey("test:effective_rule")
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            settings = mutableListOf(ExtensionRuleSetting(key, true)),
            scopeId = ScopeId(generateCompatScopeIdRaw(8, 1))
        )
        val region = Region("region", 8, mutableListOf(scope))

        assertFalse(getEffectiveRegionRuleValue(region, key))
        assertTrue(getEffectiveScopeRuleValue(region, scope, key))

        val otherScope = GeoScope(
            "other-scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            scopeId = ScopeId(generateCompatScopeIdRaw(9, 1))
        )
        val otherRegion = Region("other", 9, mutableListOf(otherScope))
        assertFailsWith<IllegalArgumentException> {
            getEffectiveScopeRuleValue(otherRegion, scope, key)
        }
    }
}
