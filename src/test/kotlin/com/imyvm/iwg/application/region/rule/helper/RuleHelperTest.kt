package com.imyvm.iwg.application.region.rule.helper

import com.imyvm.iwg.application.interaction.getDefaultValueForRule
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            getDefaultValueForRule(RuleKey.DISPENSER),
            getEffectiveScopeRuleValue(region, scope, RuleKey.DISPENSER)
        )
        assertFalse(getEffectiveScopeRuleValue(region, scope, RuleKey.PISTON))
        assertTrue(getEffectiveScopeRuleValue(region, scope, RuleKey.SPAWN_MONSTERS))
        assertTrue(getEffectiveScopeRuleValue(region, scope, RuleKey.TNT_BLOCK_PROTECTION))
    }
}
