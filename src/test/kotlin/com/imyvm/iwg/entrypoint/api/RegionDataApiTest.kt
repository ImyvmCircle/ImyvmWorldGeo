package com.imyvm.iwg.inter.api

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.component.*
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegionDataApiTest {
    @Test
    fun `region scopes are returned as a snapshot`() {
        val first = scope("first")
        val region = Region("region", 1, mutableListOf(first))

        val snapshot = RegionDataApi.getRegionScopes(region)
        region.addOwnedScope(scope("second", 2))

        assertEquals(listOf(first), snapshot)
    }

    @Test
    fun `timed overlay factory snapshots effects`() {
        val effects = mutableListOf(TimedEffect(EffectKey.SPEED, 1))
        val scopeId = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(1, 0)))

        val overlay = RegionDataApi.createTimedEffectOverlay(
            "overlay", scopeId, effects, 0, 1, 0, "test"
        )
        effects.clear()

        assertEquals(listOf(TimedEffect(EffectKey.SPEED, 1)), overlay.effects)
        assertFailsWith<UnsupportedOperationException> { (overlay.effects as MutableList).clear() }
    }

    @Test
    fun `getEffectiveRulesForScope returns all RuleKey entries with defaults for empty stores`() {
        val s = scope("s")
        val region = Region("region", 1, mutableListOf(s))

        val rules = RegionDataApi.getEffectiveRulesForScope(region, s)
        assertEquals(RuleKey.entries.toSet(), rules.keys)
    }

    @Test
    fun `getEffectiveRulesForScope region override flows through`() {
        val s = scope("s")
        val region = Region("region", 1, mutableListOf(s),
            settings = mutableListOf(RuleSetting(RuleKey.SPAWN_MONSTERS, false)))

        val rules = RegionDataApi.getEffectiveRulesForScope(region, s)
        assertEquals(false, rules[RuleKey.SPAWN_MONSTERS])
    }

    @Test
    fun `getEffectiveRulesForScope scope override takes priority over region`() {
        val s = scope("s", settings = mutableListOf(RuleSetting(RuleKey.SPAWN_MONSTERS, true)))
        val region = Region("region", 1, mutableListOf(s),
            settings = mutableListOf(RuleSetting(RuleKey.SPAWN_MONSTERS, false)))

        val rules = RegionDataApi.getEffectiveRulesForScope(region, s)
        assertEquals(true, rules[RuleKey.SPAWN_MONSTERS])
    }

    @Test
    fun `getEffectiveRulesForScope rejects foreign scope`() {
        val ownedScope = scope("owned")
        val foreignScope = scope("foreign")
        val region = Region("region", 1, mutableListOf(ownedScope))

        assertFailsWith<IllegalArgumentException> {
            RegionDataApi.getEffectiveRulesForScope(region, foreignScope)
        }
    }

    @Test
    fun `explicit extension rule queries resolve default, region, and scope`() {
        ExtensionSettingRegistry.registerRuleKey("test:rule.b6", true)
        val s = scope("s", settings = mutableListOf(
            ExtensionRuleSetting(ExtensionRuleKey("test:rule.b6"), false)
        ))
        val region = Region("region", 1, mutableListOf(s))

        assertEquals(true, RegionDataApi.getDefaultExtensionRuleValue("test:rule.b6"))
        assertEquals(true, RegionDataApi.getRegionExtensionRuleValue(region, "test:rule.b6"))
        assertEquals(false, RegionDataApi.getScopeExtensionRuleValue(region, s, "test:rule.b6"))
    }

    @Test
    fun `extension rule region override`() {
        ExtensionSettingRegistry.registerRuleKey("test:rule.b6.region", false)
        val s = scope("s")
        val region = Region("region", 1, mutableListOf(s),
            settings = mutableListOf(ExtensionRuleSetting(ExtensionRuleKey("test:rule.b6.region"), true)))

        assertEquals(true, RegionDataApi.getRegionExtensionRuleValue(region, "test:rule.b6.region"))
        assertEquals(true, RegionDataApi.getScopeExtensionRuleValue(region, s, "test:rule.b6.region"))
    }

    @Test
    fun `getScopeExtensionRuleValue rejects foreign scope`() {
        ExtensionSettingRegistry.registerRuleKey("test:rule.b6.foreign", true)
        val ownedScope = scope("owned")
        val foreignScope = scope("foreign")
        val region = Region("region", 1, mutableListOf(ownedScope))

        assertFailsWith<IllegalArgumentException> {
            RegionDataApi.getScopeExtensionRuleValue(region, foreignScope, "test:rule.b6.foreign")
        }
    }

    @Test
    fun `unregistered extension rule key fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RegionDataApi.getDefaultExtensionRuleValue("test:unregistered.rule.key")
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `deprecated getExtensionRuleValueForRegion delegates correctly`() {
        ExtensionSettingRegistry.registerRuleKey("test:rule.b6.compat", true)
        val s = scope("s", settings = mutableListOf(
            ExtensionRuleSetting(ExtensionRuleKey("test:rule.b6.compat"), false)
        ))
        val region = Region("region", 1, mutableListOf(s))

        assertEquals(true, RegionDataApi.getExtensionRuleValueForRegion(null, null, "test:rule.b6.compat"))
        assertEquals(true, RegionDataApi.getExtensionRuleValueForRegion(region, null, "test:rule.b6.compat"))
        assertEquals(false, RegionDataApi.getExtensionRuleValueForRegion(region, s, "test:rule.b6.compat"))
    }

    private fun scope(name: String, id: Long = 1, settings: MutableList<Setting> = mutableListOf()) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        settings = settings,
        scopeId = ScopeId(generateCompatScopeIdRaw(1, id.toInt()))
    )
}
