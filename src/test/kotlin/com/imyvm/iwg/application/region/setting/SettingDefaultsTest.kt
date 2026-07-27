package com.imyvm.iwg.application.region.setting

import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingDefaultsTest {
    @Test
    fun `built in default catalogs cover every key`() {
        PermissionKey.entries.forEach(::defaultPermissionValue)
        RuleKey.entries.forEach(::defaultRuleValue)
    }

    @Test
    fun `extension defaults come from the registered key catalog`() {
        ExtensionSettingRegistry.registerPermissionKey("test:default_permission", false)
        ExtensionSettingRegistry.registerRuleKey("test:default_rule", true)

        assertFalse(defaultPermissionValue(ExtensionSettingRegistry.permissionKey("test:default_permission")))
        assertTrue(defaultRuleValue(ExtensionSettingRegistry.ruleKey("test:default_rule")))
    }

    @Test
    fun `unregistered extension defaults fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            defaultPermissionValue(ExtensionPermissionKey("test:missing_permission"))
        }
        assertFailsWith<IllegalArgumentException> {
            defaultRuleValue(ExtensionRuleKey("test:missing_rule"))
        }
    }
}
