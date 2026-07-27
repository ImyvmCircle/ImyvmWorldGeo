package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.permission.helper.getEffectiveRegionGlobalPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveScopePlayerPermissionValue
import com.imyvm.iwg.application.region.setting.defaultPermissionValue
import com.imyvm.iwg.application.region.setting.defaultRuleValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingApplicationCompatibilityTest {
    private val playerUUID = UUID.randomUUID()
    private val scope = GeoScope(
        "scope",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(10, 1))
    )
    private val region = Region("region", 10, mutableListOf(scope))

    @Test
    fun `retained permission and default wrappers delegate to typed implementations`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))
        scope.settingStore.put(PermissionSetting(PermissionKey.PVP, true, playerUUID))

        assertEquals(
            getEffectiveRegionGlobalPermissionValue(region, PermissionKey.PVP),
            getRegionPermissionValue(region, PermissionKey.PVP)
        )
        assertEquals(
            getEffectiveScopePlayerPermissionValue(region, scope, playerUUID, PermissionKey.PVP),
            getScopePermissionValue(region, scope, playerUUID, PermissionKey.PVP)
        )
        assertEquals(defaultPermissionValue(PermissionKey.FLY), getDefaultValueForPermission(PermissionKey.FLY))
        assertEquals(defaultRuleValue(RuleKey.PISTON), getDefaultValueForRule(RuleKey.PISTON))
    }

    @Test
    fun `retained extension default dispatchers preserve nullable boundary behavior`() {
        ExtensionSettingRegistry.registerPermissionKey("test:compat_permission", false)
        ExtensionSettingRegistry.registerRuleKey("test:compat_rule", true)
        val permissionKey = ExtensionSettingRegistry.permissionKey("test:compat_permission")

        assertEquals(
            defaultPermissionValue(permissionKey),
            onCertificateExtensionPermissionValue(null, null, null, "test:compat_permission")
        )
        assertEquals(
            defaultRuleValue(ExtensionSettingRegistry.ruleKey("test:compat_rule")),
            getEffectiveExtensionRuleValue(null, null, "test:compat_rule")
        )
    }
}
