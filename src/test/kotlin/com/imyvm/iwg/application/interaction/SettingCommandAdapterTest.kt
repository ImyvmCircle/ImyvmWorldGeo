package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingCommandAdapterTest {
    @Test
    fun `setting key parser narrows every built in family`() {
        assertEquals(PermissionKey.BUILD, requireRegisteredSettingKey("BUILD"))
        assertEquals(EffectKey.SPEED, requireRegisteredSettingKey("SPEED"))
        assertEquals(RuleKey.PISTON, requireRegisteredSettingKey("PISTON"))
        assertEquals(
            EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED,
            requireRegisteredSettingKey("ENTRY_EXIT_MESSAGE_ENABLED")
        )
        assertEquals(EntryExitMessageKey.ENTER_MESSAGE, requireRegisteredSettingKey("ENTER_MESSAGE"))
    }

    @Test
    fun `setting key parser narrows registered extensions`() {
        ExtensionSettingRegistry.registerPermissionKey("test:parser_permission", true)
        ExtensionSettingRegistry.registerRuleKey("test:parser_rule", false)

        assertIs<ExtensionPermissionKey>(requireRegisteredSettingKey("test:parser_permission"))
        assertIs<ExtensionRuleKey>(requireRegisteredSettingKey("test:parser_rule"))
    }

    @Test
    fun `permission parser rejects other families and unknown keys`() {
        assertFailsWith<IllegalArgumentException> {
            requireRegisteredPermissionKey("PISTON")
        }
        assertFailsWith<IllegalArgumentException> {
            requireRegisteredSettingKey("test:unknown")
        }
    }

    @Test
    fun `boolean and effect parsing enforce exact command ranges`() {
        assertEquals(true, parseBooleanSettingValue("true"))
        assertEquals(false, parseBooleanSettingValue("false"))
        assertNull(parseBooleanSettingValue("TRUE"))
        assertEquals(0, parseEffectAmplifier("0"))
        assertEquals(255, parseEffectAmplifier("255"))
        assertNull(parseEffectAmplifier("-1"))
        assertNull(parseEffectAmplifier("256"))
        assertNull(parseEffectAmplifier("not-an-int"))
    }
}
